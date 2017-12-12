# coding=utf-8
# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import

from pants.build_graph.build_file_aliases import BuildFileAliases
from pants.goal.task_registrar import TaskRegistrar as task

from fsqio.pants.buildgen.core.buildgen import Buildgen
from fsqio.pants.buildgen.core.buildgen_aggregate_targets import BuildgenAggregateTargets
from fsqio.pants.buildgen.core.buildgen_target_bag import BuildgenTargetBag
from fsqio.pants.buildgen.core.inject_target_bags import BuildgenInjectTargetBags
from fsqio.pants.buildgen.core.map_derived_targets import MapDerivedTargets
from fsqio.pants.buildgen.core.map_sources_to_addresses import MapSourcesToAddresses
from fsqio.pants.buildgen.core.subsystems.buildgen_subsystem import BuildgenSubsystem


def build_file_aliases():
  return BuildFileAliases(
    targets={
      'buildgen_target_bag': BuildgenTargetBag,
    },
  )


def global_subsystems():
  return (BuildgenSubsystem.Factory,)


def register_goals():
  task(
    name='map-derived-targets',
    action=MapDerivedTargets,
  ).install()

  task(
    name='map-sources-to-addresses-mapper',
    action=MapSourcesToAddresses,
  ).install()

  task(
    name='buildgen',
    action=Buildgen,
  ).install()

  task(
    name='add-target-bags',
    action=BuildgenInjectTargetBags,
  ).install('test')

  task(
    name='aggregate-targets',
    action=BuildgenAggregateTargets,
  ).install('buildgen')
