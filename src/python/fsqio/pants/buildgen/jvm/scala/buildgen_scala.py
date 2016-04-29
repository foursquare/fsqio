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

from pants.backend.jvm.targets.java_tests import JavaTests
from pants.backend.jvm.targets.scala_library import ScalaLibrary

from fsqio.pants.buildgen.core.buildgen_task import BuildgenTask


class BuildgenScala(BuildgenTask):
  @classmethod
  def prepare(cls, options, round_manager):
    super(BuildgenScala, cls).prepare(options, round_manager)
    round_manager.require_data('java_source_to_exported_symbols')
    round_manager.require_data('scala')
    round_manager.require_data('scala_library_to_used_addresses')
    round_manager.require_data('scala_source_to_exported_symbols')
    round_manager.require_data('scala_source_to_used_symbols')
    round_manager.require_data('soy_source_to_external_call_symbols')

  @classmethod
  def product_types(cls):
    return [
      'buildgen_scala',
    ]

  @property
  def types_operated_on(self):
    return (ScalaLibrary, JavaTests)

  @property
  def _scala_library_to_used_addresses(self):
    return self.context.products.get_data('scala_library_to_used_addresses')

  def buildgen_target(self, scala_target):
    addresses_used_by_target = set(
      self.context.build_graph.get_target(addr).concrete_derived_from.address
      for addr in self._scala_library_to_used_addresses[scala_target]
    )
    filtered_addresses_used_by_target = set(
      addr for addr in addresses_used_by_target
      if addr != scala_target.address
    )
    self.adjust_target_build_file(scala_target, filtered_addresses_used_by_target)
