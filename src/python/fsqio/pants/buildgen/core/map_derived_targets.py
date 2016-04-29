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

from collections import defaultdict

from pants.task.task import Task


class MapDerivedTargets(Task):
  """Provides a product mapping concrete targets to all of their synthetic derivatives."""

  @classmethod
  def product_types(cls):
    return [
      'concrete_target_to_derivatives',
    ]

  @classmethod
  def prepare(cls, options, round_manager):
    # It is not necessarily JVM specific but it must be scheduled after codegen.
    round_manager.require_data('java')
    round_manager.require_data('python')
    round_manager.require_data('scala')

  def execute(self):
    concrete_target_to_derivatives = defaultdict(set)
    for target in self.context.build_graph.targets():
      if target.is_synthetic and target.derived_from is not None:
        concrete_target_to_derivatives[target.concrete_derived_from].add(target)
    products = self.context.products
    products.safe_create_data('concrete_target_to_derivatives',
                              lambda: concrete_target_to_derivatives)
