# coding=utf-8
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import

from pants.build_graph.build_file_aliases import BuildFileAliases
from pants.goal.goal import Goal
from pants.goal.task_registrar import TaskRegistrar as task

from fsqio.pants.node.subsystems.resolvers.webpack_resolver import WebPackResolver
from fsqio.pants.node.targets.webpack_module import WebPackModule
from fsqio.pants.node.tasks.webpack import WebPack
from fsqio.pants.node.tasks.webpack_bundle import WebPackBundle
from fsqio.pants.node.tasks.webpack_resolve import WebPackResolve
from fsqio.pants.node.tasks.webpack_test_run import WebPackTest, WebPackTestRun


def build_file_aliases():
  return BuildFileAliases(
    targets={
      'webpack_module': WebPackModule,
      'webpack_test': WebPackTest,
    },
  )

def global_subsystems():
  return (WebPackResolver,)

def register_goals():
  Goal.register('webpack', 'Build Node.js webpack modules.')

  # These are incompatible with our subclasses at the moment.
  Goal.by_name('test').uninstall_task('node')
  Goal.by_name('resolve').uninstall_task('node')

  # Install our webpack-focused node tasks.
  task(name='webpack-resolve', action=WebPackResolve).install('webpack')
  task(name='webpack-gen', action=WebPack).install('webpack')
  task(name='webpack-bundle', action=WebPackBundle).install('bundle')
  task(name='webpack', action=WebPackTestRun).install('test')
