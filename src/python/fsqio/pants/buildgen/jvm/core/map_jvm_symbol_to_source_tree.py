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

from pants.task.task import Task

from fsqio.pants.buildgen.core.symbol_tree import SymbolTreeNode


class MapJvmSymbolToSourceTree(Task):
  """A prefix tree mapping JVM symbols to sources that export that symbol."""

  @classmethod
  def product_types(cls):
    return [
      'jvm_symbol_to_source_tree',
    ]

  @classmethod
  def prepare(cls, options, round_manager):
    round_manager.require_data('scala')
    round_manager.require_data('java_source_to_exported_symbols')
    round_manager.require_data('scala_source_to_exported_symbols')

  def execute(self):
    products = self.context.products
    scala_source_to_exported_symbols = products.get_data('scala_source_to_exported_symbols')
    jvm_symbol_to_source_tree = SymbolTreeNode()
    for source, analysis in scala_source_to_exported_symbols.items():
      exported_symbols = analysis['exported_symbols']
      for symbol in exported_symbols:
        jvm_symbol_to_source_tree.insert(symbol, source)
    java_source_to_exported_symbols = products.get_data('java_source_to_exported_symbols')
    for source, symbols in java_source_to_exported_symbols.items():
      for symbol in symbols:
        jvm_symbol_to_source_tree.insert(symbol, source)

    products.safe_create_data('jvm_symbol_to_source_tree',
                              lambda: jvm_symbol_to_source_tree)
