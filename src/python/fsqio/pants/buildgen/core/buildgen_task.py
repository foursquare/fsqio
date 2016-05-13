# coding=utf-8
# Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

from __future__ import (
  absolute_import,
  division,
  generators,
  nested_scopes,
  print_function,
  unicode_literals,
  with_statement,
)

from copy import deepcopy

from pants.util.memo import memoized_property

from fsqio.pants.buildgen.core.build_file_manipulator import BuildFileManipulator
from fsqio.pants.buildgen.core.buildgen_base import BuildgenBase
from fsqio.pants.buildgen.core.third_party_map_util import merge_map


class BuildgenTask(BuildgenBase):

  @classmethod
  def prepare(cls, options, round_manager):
    round_manager.require('concrete_target_to_derivatives')
    round_manager.require('source_to_addresses_mapper')

  @memoized_property
  def dryrun(self):
    return self.buildgen_subsystem.dry_run

  @memoized_property
  def target_alias_whitelist(self):
    return self.buildgen_subsystem.target_alias_whitelist

  @memoized_property
  def target_alias_blacklist(self):
    """Subclasses may implement to list target aliases that should not be managed by that task.

    For example, a buildgem task may understand JvmLibrary but not its subclass ScalaLibrary.
    """
    return []

  @memoized_property
  def managed_dependency_aliases(self):
    return self.buildgen_subsystem.managed_dependency_aliases

  @memoized_property
  def third_party_target_aliases(self):
    """List of target_aliases that are acceptable 3rdparty libraries for a language."""
    return []

  @memoized_property
  def merged_map(self):
    """Returns the recursively updated mapping of imports to third party BUILD file entries.

    Entries passed to the option system take priority.
    """
    # TODO(mateo): Make the third_party map a Task property and move this to super class?
    merged_map = deepcopy(self.third_party_map)
    merge_map(merged_map, self.get_options().additional_third_party_map)
    return merged_map

  def adjust_target_build_file(self, target, computed_dep_addresses, whitelist=None):
    """Makes a BuildFileManipulator and adjusts the BUILD file to reflect the computed addresses"""
    alias_whitelist = whitelist or self.buildgen_subsystem.target_alias_whitelist
    manipulator = BuildFileManipulator.load(target.address.build_file,
                                            target.address.target_name,
                                            alias_whitelist)

    existing_dep_addresses = manipulator.get_dependency_addresses()
    for address in existing_dep_addresses:
      if not self.context.build_graph.get_target(address):
        self.context.build_graph.inject_address(address)

    existing_deps = [self.context.build_graph.get_target(address)
                     for address in existing_dep_addresses]
    ignored_deps = [dep for dep in existing_deps
                    if dep.type_alias not in self.managed_dependency_aliases]

    manipulator.clear_unforced_dependencies()
    for ignored_dep in ignored_deps:
      manipulator.add_dependency(ignored_dep.address)
    for address in computed_dep_addresses:
      manipulator.add_dependency(address)

    final_dep_addresses = manipulator.get_dependency_addresses()
    manipulator.write(dry_run=self.dryrun, use_colors=self.get_options().colors)

  def execute(self):
    def task_targets():
      for target in self.context.target_roots:
        if isinstance(target, self.types_operated_on) and target.type_alias not in self.target_alias_blacklist:
          yield target
    targets = sorted(list(task_targets()))
    if self.get_options().level == 'debug':
      print('\n{0} will operate on the following targets:'.format(type(self).__name__))
      for target in targets:
        print('* {0}'.format(target.address.reference()))
    for target in targets:
      self.buildgen_target(target)
