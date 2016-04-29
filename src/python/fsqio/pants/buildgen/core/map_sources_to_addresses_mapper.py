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

from pants.build_graph.source_mapper import LazySourceMapper
from pants.task.task import Task


class MapSourcesToAddressesMapper(Task):
  """Map sources to addresses.

  Tasks that buildgen for a target type can extend this task's product with their own source -> address mapping.
  """

  @classmethod
  def product_types(cls):
    return [
      'source_to_addresses_mapper',
    ]

  @classmethod
  def prepare(cls, options, round_manager):
    # Not JVM specific but needed to ensure codegen is run before buildgen.
    round_manager.require_data('java')
    round_manager.require_data('scala')

  def execute(self):
    products = self.context.products
    source_to_addresses_mapper = LazySourceMapper(
      self.context.address_mapper,
      self.context.build_graph,
      stop_after_match=True,
    )
    for target in self.context.build_graph.targets():
      for source in target.sources_relative_to_buildroot():
        source_to_addresses_mapper._source_to_address[source].add(target.address)
    products.safe_create_data('source_to_addresses_mapper',
                              lambda: source_to_addresses_mapper)
