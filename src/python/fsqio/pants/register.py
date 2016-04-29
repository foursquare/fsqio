# coding=utf-8
# Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import

import os

from pants.backend.jvm.repository import Repository
from pants.base.build_environment import get_buildroot
from pants.build_graph.build_file_aliases import BuildFileAliases
from pants.goal.goal import Goal
from pants.goal.task_registrar import TaskRegistrar as task
from pants.task.task import Task

from fsqio.pants.buildgen.buildgen_spindle import BuildgenSpindle
from fsqio.pants.buildgen.core.buildgen import Buildgen
from fsqio.pants.buildgen.core.buildgen_aggregate_targets import BuildgenAggregateTargets
from fsqio.pants.buildgen.core.buildgen_target_bag import BuildgenTargetBag
from fsqio.pants.buildgen.core.buildgen_timestamp import BuildgenTimestamp
from fsqio.pants.buildgen.core.map_derived_targets import MapDerivedTargets
from fsqio.pants.buildgen.core.map_sources_to_addresses_mapper import MapSourcesToAddressesMapper
from fsqio.pants.buildgen.jvm.map_java_exported_symbols import MapJavaExportedSymbols
from fsqio.pants.buildgen.jvm.map_jvm_symbol_to_source_tree import MapJvmSymbolToSourceTree
from fsqio.pants.buildgen.jvm.map_third_party_jar_symbols import MapThirdPartyJarSymbols
from fsqio.pants.buildgen.jvm.scala.buildgen_scala import BuildgenScala
from fsqio.pants.buildgen.jvm.scala.map_scala_library_used_addresses import (
  MapScalaLibraryUsedAddresses,
)
from fsqio.pants.buildgen.jvm.scala.scala_exported_symbols import MapScalaExportedSymbols
from fsqio.pants.buildgen.jvm.scala.scala_used_symbols import MapScalaUsedSymbols
from fsqio.pants.pom.pom_publish import PomPublish, PomTarget
from fsqio.pants.pom.pom_resolve import PomResolve
from fsqio.pants.spindle.targets.spindle_thrift_library import SpindleThriftLibrary
from fsqio.pants.spindle.targets.ssp_template import SspTemplate
from fsqio.pants.spindle.tasks.build_spindle import BuildSpindle
from fsqio.pants.spindle.tasks.spindle_gen import SpindleGen
from fsqio.pants.validate import Tagger, Validate


oss_sonatype_repo = Repository(
  name='oss_sonatype_repo',
  url='https://oss.sonatype.org/#stagingRepositories',
  push_db_basedir=os.path.join(get_buildroot(), 'pushdb'),
)

def build_file_aliases():
  return BuildFileAliases(
    targets={
      'buildgen_target_bag': BuildgenTargetBag,
      'spindle_thrift_library': SpindleThriftLibrary,
      'scala_record_library': SpindleThriftLibrary,
      'ssp_template': SspTemplate,
      'pom_target': PomTarget,
    },
    objects={
      'oss_sonatype_repo': oss_sonatype_repo,
    },
  )

def register_goals():
  task(name='tag',
       action=Tagger).install()

  task(name='validate',
       action=Validate).install()
  # TODO: Once we have validation logic that passes cleanly, add the validate goal as a
  # dependency of, say, the thrift goal, to ensure validate is always invoked when compiling.

  class ForceValidation(Task):
    @classmethod
    def prepare(cls, options, round_manager):
      round_manager.require_data('validated_build_graph')

    def execute(self):
      pass

  task(
    name='validate-graph',
    action=ForceValidation,
  ).install('gen', replace=True)

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
    name='map-derived-targets',
    action=MapDerivedTargets,
  ).install()

  task(
    name='map-sources-to-addresses-mapper',
    action=MapSourcesToAddressesMapper,
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
    name='buildgen',
    action=Buildgen,
  ).install()

  task(
    name='scala',
    action=BuildgenScala,
  ).install('buildgen')

  task(
    name='spindle',
    action=BuildgenSpindle,
  ).install('buildgen')

  task(
    name='aggregate-targets',
    action=BuildgenAggregateTargets,
  ).install('buildgen')

  task(
    name='timestamp',
    action=BuildgenTimestamp,
  ).install('buildgen')

  Goal.by_name('resolve').uninstall_task('ivy')
  task(name='pom-resolve', action=PomResolve).install()
  task(name='pom-publish', action=PomPublish).install()

  task(name='build-spindle', action=BuildSpindle).install()
  task(name='spindle', action=SpindleGen).install('gen')


