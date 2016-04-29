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

from os import path as P

from pants.backend.jvm.targets.java_tests import JavaTests
from pants.backend.jvm.targets.scala_library import ScalaLibrary
from pants.backend.jvm.tasks.nailgun_task import NailgunTask

from fsqio.pants.buildgen.core.source_analysis_task import SourceAnalysisTask
from fsqio.pants.buildgen.jvm.scalac_buildgen_task_mixin import ScalacBuildgenTaskMixin


class MapScalaUsedSymbols(ScalacBuildgenTaskMixin, SourceAnalysisTask, NailgunTask):
  """Provides a product mapping source files to the symbols used by that source."""
  @classmethod
  def analysis_product_name(cls):
    return 'scala_source_to_used_symbols'

  @property
  def claimed_target_types(self):
    return (ScalaLibrary, JavaTests)

  @classmethod
  def register_options(cls, register):
    super(MapScalaUsedSymbols, cls).register_options(register)
    cls.register_scalac_buildgen_jvm_tools(register)

  def is_analyzable(self, source):
    return P.splitext(source)[1] == '.scala'

  @classmethod
  def prepare(cls, options, round_manager):
    super(MapScalaUsedSymbols, cls).prepare(options, round_manager)
    round_manager.require_data('scala_source_to_exported_symbols')
    round_manager.require_data('third_party_jar_symbols')
    round_manager.require_data('jvm_build_tools_classpath_callbacks')

  def analyze_sources(self, sources):
    products = self.context.products
    scala_source_to_exported_symbols = products.get_data('scala_source_to_exported_symbols')
    third_party_jar_symbols = products.get_data('third_party_jar_symbols')
    symbol_whitelist = third_party_jar_symbols.copy()
    for _, symbols in scala_source_to_exported_symbols.items():
      symbol_whitelist.update(set(symbols['exported_symbols']))
    return self.map_used_symbols(
      sources=sources,
      java_runner=self.runjava,
      whitelist=symbol_whitelist,
    )
