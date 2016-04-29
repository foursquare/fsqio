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

import colors
import sys

from pants.contrib.buildgen.build_file_manipulator import BuildFileManipulator
from pants.util.memo import memoized_property

from fsqio.pants.buildgen.core.buildgen_base import BuildgenBase
from fsqio.pants.buildgen.subsystems.buildgen_subsystem import BuildgenSubsystem


class BuildgenTask(BuildgenBase):

  @classmethod
  def prepare(cls, options, round_manager):
    # Hack the planet.
    subsystem = BuildgenSubsystem.Factory.global_instance().create()
    products = subsystem.required_products
    for product in products:
      round_manager.require_data(product)

  @memoized_property
  def dryrun(self):
    return self.buildgen_subsystem.dry_run

  @memoized_property
  def style_run(self):
    return self.buildgen_subsystem.style_run

  @memoized_property
  def target_alias_whitelist(self):
    return self.buildgen_subsystem.target_alias_whitelist

  @memoized_property
  def managed_dependency_aliases(self):
    return self.buildgen_subsystem.managed_dependency_aliases

  @memoized_property
  def required_products(self):
    return self.buildgen_subsystem.required_products

  def adjust_target_build_file(self, target, computed_dep_addresses, whitelist=None):
    """Makes a BuildFileManipulator and adjusts the BUILD file to reflect the computed addresses"""
    # TODO(mateo): Upstream module in contrib no longer matches this signature, adjust it to match after this is OSS.
    alias_whitelist = whitelist or self.buildgen_subsystem.target_alias_whitelist
    manipulator = BuildFileManipulator.load(target.address.build_file,
                                            target.address.target_name,
                                            alias_whitelist)

    existing_dep_addresses = manipulator._dependencies_by_address.keys()

    for address in existing_dep_addresses:
      if not self.context.build_graph.get_target(address):
        self.context.build_graph.inject_address(address)

    existing_deps = [self.context.build_graph.get_target(address)
                     for address in existing_dep_addresses]
    # Note that I changed the type check here, from a Target (e.g. ScalaLibrary) to a type_alias (e.g. 'scala_library')
    ignored_deps = [dep for dep in existing_deps
                    if dep.type_alias not in self.managed_dependency_aliases]

    manipulator.clear_unforced_dependencies()
    for ignored_dep in ignored_deps:
      manipulator.add_dependency(ignored_dep.address)
    for address in computed_dep_addresses:
      manipulator.add_dependency(address)

    final_dep_addresses = manipulator._dependencies_by_address.keys()

    style_only = set(final_dep_addresses) == set(existing_dep_addresses)

    diff_lines = manipulator.diff_lines()
    if diff_lines:
      if self.dryrun:
        msg = colors.yellow('DRY RUN, would have written this diff:')
      elif self.style_run and style_only:
        msg = colors.green('STYLE RUN, about to write this (style only) diff:')
      else:
        msg = colors.blue('REAL RUN, about to write the following diff:')
      sys.stderr.write('\n\n' + msg + '\n')
      sys.stderr.write(colors.yellow('*' * 40 + '\n'))
      sys.stderr.write('target at: ')
      sys.stderr.write(str(target.address) + '\n')
      for line in diff_lines:
        color_fn = lambda x: x
        if line.startswith('+') and not line.startswith('+++'):
          color_fn = colors.green
        elif line.startswith('-') and not line.startswith('---'):
          color_fn = colors.red
        sys.stderr.write(color_fn(line) + '\n')
      sys.stderr.write(colors.yellow('*' * 40 + '\n'))
      if not self.dryrun or (self.style_run and style_only):
        with open(manipulator.build_file.full_path, 'w') as f:
          f.write('\n'.join(manipulator.build_file_lines()))

  def execute(self):
    def task_targets():
      for target in self.context.target_roots:
        if isinstance(target, self.types_operated_on):
          yield target
    targets = sorted(list(task_targets()))
    if self.get_options().level == 'debug':
      print('\n{0} will operate on the following targets:'.format(type(self).__name__))
      for target in targets:
        print('* {0}'.format(target.address.reference()))
    for target in targets:
      self.buildgen_target(target)
