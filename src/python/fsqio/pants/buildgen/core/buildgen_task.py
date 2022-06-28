# coding=utf-8
# Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function, unicode_literals

from copy import deepcopy
from itertools import chain

from pants.base.exceptions import TaskError
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
  def target_alias_allowlist(self):
    return self.buildgen_subsystem.target_alias_allowlist

  @memoized_property
  def target_alias_blocklist(self):
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

  def get_provides(self, target):
    """Returns publication configuration gathered from target context."""
    # Subclasses must implement support.
    return None

  @property
  def artifact_type(self):
    """Returns desired publication artifact type as a string."""
    return None

  def filtered_target_addresses(self, addresses):
    for address in addresses:
      target = self.context.build_graph.get_target(address)
      if target.type_alias in self.supported_target_aliases:
        yield address

  def included_addresses(self, source_dependencies, target):
    included_addresses = set(chain.from_iterable(
      self.filtered_target_addresses(self._source_mapper.target_addresses_for_source(source))
      for source in source_dependencies
    ))
    return {addr for addr in included_addresses if addr != target.address}

  def adjust_target_build_file(self, target, computed_dep_addresses, allowlist=None):
    """Makes a BuildFileManipulator and adjusts the BUILD file to reflect the computed addresses"""
    alias_allowlist = allowlist or self.buildgen_subsystem.target_alias_allowlist

    # Tasks can add publication support by overriding `get_provides` and `artifact_type`,
    # otherwise set to None.
    # The manipulator needed a bigger rework to do this more gracefully.
    provides = self.get_provides(target)

    if provides and self.artifact_type is None:
      raise TaskError
    manipulator = BuildFileManipulator.load(
      target.address,
      alias_allowlist,
      artifact_type=self.artifact_type,
      provides=provides
    )

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

    manipulator.write(
      dry_run=self.dryrun,
      use_colors=self.get_options().colors,
      fail_on_diff=self.get_options().fail_on_diff
    )

  def execute(self):
    def task_targets():
      for target in self.context.target_roots:
        # TODO(mateo): Rework these type checks now that they have all settled on string comparisons.
        # NOTE: For instance - can we combine 'supported_target_aliases' and 'managed_dependency_aliases'?
        # Probably the best compromise would be to go to labels, i.e. 'if t.is_scala or (t.is_java && t.is_test)'
        # while supporting the blocklisting of a given type_aliases as needed.
        # Using labels would allow us to avoid importing arbitrary target types, continue to set these values in config,
        # while also giving us the benefit of the type system managing subclasses and so forth.

        if target.type_alias in self.supported_target_aliases and target.type_alias not in self.target_alias_blocklist:
          yield target
    targets = sorted(list(task_targets()))
    if self.get_options().level == 'debug':
      print('\n{0} will operate on the following targets:'.format(type(self).__name__))
      for target in targets:
        print('* {0}'.format(target.address.reference()))
    for target in targets:
      self.buildgen_target(target)
