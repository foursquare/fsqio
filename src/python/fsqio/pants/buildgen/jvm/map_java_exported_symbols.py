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

from pants.backend.jvm.targets.java_library import JavaLibrary
from pants.backend.jvm.targets.scala_library import ScalaLibrary
from pants.base.build_environment import get_buildroot
from pants.task.task import Task


class MapJavaExportedSymbols(Task):
  """A naive map of java sources to the symbols they export.

    We just assume that each java source file represents a single symbol
    defined by the directory structure (from the source root) and terminating in the name of the
    file with '.java' stripped off.
    """

  @classmethod
  def product_types(cls):
    return [
      'java_source_to_exported_symbols',
    ]

  @classmethod
  def prepare(cls, options, round_manager):
    round_manager.require_data('java')

  def execute(self):
    java_source_to_exported_symbols = defaultdict(set)

    def is_java_lib(t):
      return isinstance(t, (JavaLibrary, ScalaLibrary))

    for target in self.context.build_graph.targets(is_java_lib):
      for source_candidate in target.sources_relative_to_buildroot():
        abs_source = P.join(get_buildroot(), source_candidate)
        abs_source_root = P.join(get_buildroot(), target.target_base)
        source_root_relative_source = P.relpath(abs_source, abs_source_root)
        source_root_relative_dir = P.dirname(source_root_relative_source)
        source_name, source_ext = P.splitext(P.basename(source_root_relative_source))
        if source_ext == '.java':
          if source_root_relative_dir == '.':
            fq_symbol = source_name
          else:
            prefix_symbol = source_root_relative_dir.replace('/', '.')
            fq_symbol = '.'.join([prefix_symbol, source_name])
          java_source_to_exported_symbols[source_candidate].update([fq_symbol])

    products = self.context.products
    products.safe_create_data('java_source_to_exported_symbols',
                              lambda: java_source_to_exported_symbols)
