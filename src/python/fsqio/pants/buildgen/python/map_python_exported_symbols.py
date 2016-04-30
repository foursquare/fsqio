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
from os import path as P

from pants.backend.python.targets.python_target import PythonTarget
from pants.task.task import Task


class MapPythonExportedSymbols(Task):
  """A naive map of python sources to the symbols they export.

    We just assume that each python source file represents a single symbol
    defined by the directory structure (from the source root) and terminating in the name of the
    file with '.py' stripped off.
    """

  @classmethod
  def product_types(cls):
    return [
      'python_source_to_exported_symbols',
    ]

  @classmethod
  def prepare(cls, options, round_manager):
    round_manager.require_data('python')

  def execute(self):
    python_source_to_exported_symbols = defaultdict(set)

    for target in self.context.build_graph.targets(lambda t: isinstance(t, PythonTarget)):
      for source_candidate in target.sources_relative_to_source_root():
        source_root_relative_dir = P.dirname(source_candidate)
        if P.basename(source_candidate) == '__init__.py':
          continue
        if P.splitext(source_candidate)[1] == '.py':
          terminal_symbol = P.basename(source_candidate)[:-len('.py')]
          prefix_symbol = source_root_relative_dir.replace('/', '.')
          fq_symbol = '.'.join([prefix_symbol, terminal_symbol])
          source_relative_to_buildroot = P.join(target.target_base, source_candidate)
          python_source_to_exported_symbols[source_relative_to_buildroot].update([fq_symbol])

    self.context.products.safe_create_data(
      'python_source_to_exported_symbols',
      lambda: python_source_to_exported_symbols,
    )
