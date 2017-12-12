# coding=utf-8
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import

from pants.goal.task_registrar import TaskRegistrar as task

from fsqio.pants.buildgen.jvm.core.map_java_exported_symbols import MapJavaExportedSymbols
from fsqio.pants.buildgen.jvm.core.map_jvm_symbol_to_source_tree import MapJvmSymbolToSourceTree
from fsqio.pants.buildgen.jvm.core.map_third_party_jar_symbols import MapThirdPartyJarSymbols
from fsqio.pants.buildgen.jvm.scala.buildgen_scala import BuildgenScala
from fsqio.pants.buildgen.jvm.scala.map_scala_library_used_addresses import (
  MapScalaLibraryUsedAddresses,
)
from fsqio.pants.buildgen.jvm.scala.scala_exported_symbols import MapScalaExportedSymbols
from fsqio.pants.buildgen.jvm.scala.scala_used_symbols import MapScalaUsedSymbols


def register_goals():

  task(
    name='map-third-party-jar-symbols',
    action=MapThirdPartyJarSymbols,
  ).install()

  task(
    name='map-scala-exported-symbols',
    action=MapScalaExportedSymbols,
  ).install()

  task(
    name='map-scala-used-symbols',
    action=MapScalaUsedSymbols,
  ).install()

  task(
    name='map-java-exported-symbols',
    action=MapJavaExportedSymbols,
  ).install()

  task(
    name='map-jvm-symbol-to-source-tree',
    action=MapJvmSymbolToSourceTree,
  ).install()

  task(
    name='map-scala-library-used-addresses',
    action=MapScalaLibraryUsedAddresses,
  ).install()

  task(
    name='scala',
    action=BuildgenScala,
  ).install('buildgen')
