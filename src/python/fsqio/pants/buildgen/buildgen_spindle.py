# coding=utf-8
# Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

from __future__ import (
  absolute_import,
  division,
  generators,
  nested_scopes,
  print_function,
  unicode_literals,
  with_statement,
)

from itertools import chain
import os
import re

from pants.backend.jvm.targets.scala_library import ScalaLibrary

from fsqio.pants.buildgen.core.buildgen_task import BuildgenTask
from fsqio.pants.spindle.targets.spindle_thrift_library import SpindleThriftLibrary


class ThriftDependencyMapper(object):
  INCLUDE_REGEX = re.compile(r'\w*?include "(?P<include_path>.*?)"\w*$')

  def includes_from_source(self, source):
    with open(source, 'r') as f:
      for line in f.readlines():
        match = self.INCLUDE_REGEX.match(line)
        if match:
          yield match.groupdict()['include_path']

  def buildroot_relative_source(self, source):
    # TODO(pl): Get the source roots for thrift in a more
    # generic way.  Either look at the entire graph to get
    # them all, or query a proper SourceRoots API.
    source_roots = set(['src/thrift', 'test/thrift'])
    found_paths = set()
    for source_root in source_roots:
      potential_path = os.path.join(source_root, source)
      if os.path.exists(potential_path):
        found_paths.add(potential_path)
    if len(found_paths) > 1:
      raise ValueError('Multiple candidate sources were found for thrift include {source}:'
                       ' {candidates}'.format(source=source,
                                              candidates=','.join(found_paths)))
    if not found_paths:
      raise ValueError('No candidate source found under known source roots for {source}'
                       .format(source=source))
    else:
      return found_paths.pop()

  def target_source_dependencies(self, target):
    for source in target.sources_relative_to_buildroot():
      for include in self.includes_from_source(source):
        yield self.buildroot_relative_source(include)


class BuildgenSpindle(BuildgenTask):
  @classmethod
  def prepare(cls, options, round_manager):
    super(BuildgenSpindle, cls).prepare(options, round_manager)
    round_manager.require_data('concrete_target_to_derivatives')
    round_manager.require_data('java_source_to_exported_symbols')
    round_manager.require_data('jvm_symbol_to_source_tree')
    round_manager.require_data('scala')
    round_manager.require_data('scala_library_to_used_addresses')
    round_manager.require_data('scala_source_to_exported_symbols')
    round_manager.require_data('scala_source_to_used_symbols')
    round_manager.require_data('source_to_addresses_mapper')

  @classmethod
  def product_types(cls):
    return [
      'buildgen_spindle',
    ]

  @property
  def _concrete_target_to_derivatives(self):
    return self.context.products.get_data('concrete_target_to_derivatives')

  @property
  def _source_mapper(self):
    return self.context.products.get_data('source_to_addresses_mapper')

  @property
  def _scala_library_to_used_addresses(self):
    return self.context.products.get_data('scala_library_to_used_addresses')

  @property
  def types_operated_on(self):
    return (SpindleThriftLibrary,)

  def buildgen_target(self, spindle_target):
    source_dependencies = ThriftDependencyMapper().target_source_dependencies(spindle_target)
    def filtered_target_addresses(addresses, allowed_target_types=()):
      for address in addresses:
        target = self.context.build_graph.get_target(address)
        if isinstance(target, allowed_target_types):
          yield address
    included_addresses = set(chain.from_iterable(
      filtered_target_addresses(
        self._source_mapper.target_addresses_for_source(source),
        SpindleThriftLibrary,
      )
      for source in source_dependencies
    ))
    included_addresses = set(addr for addr in included_addresses if addr != spindle_target.address)
    synthetic_scala_targets = list(
      t for t in self._concrete_target_to_derivatives[spindle_target]
      if isinstance(t, ScalaLibrary)
    )
    if len(synthetic_scala_targets) != 1:
      raise ValueError('Could not find synthetic scala codegen target while attempting'
                       ' to buildgen spindle target {0}'.format(spindle_target.address.spec))
    synthetic_scala_target = synthetic_scala_targets[0]
    addresses_used_by_generated_code = set(
      self.context.build_graph.get_target(addr).concrete_derived_from.address
      for addr in self._scala_library_to_used_addresses[synthetic_scala_target]
    )
    # NOTE(pl): We also generate Java code for spindle, but right now buildgen isn't aware
    # of the dependencies implied by Java.  Moreover, the Java used symbols should be
    # dependency free other than dependencies automatically injected by configuration.
    all_addresses = included_addresses | addresses_used_by_generated_code
    thrift_implicit_deps = set(
      self.context.options.for_scope('gen.spindle').runtime_dependency
    )
    filtered_addresses = set(dep for dep in all_addresses if
                             dep.spec not in thrift_implicit_deps and
                             dep != spindle_target.address)
    self.adjust_target_build_file(spindle_target, filtered_addresses)
