# coding=utf-8
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import

from pants.build_graph.build_file_aliases import BuildFileAliases
from pants.goal.goal import Goal
from pants.goal.task_registrar import TaskRegistrar as task

from fsqio.pants.node.targets.webpack_module import WebPackModule
from fsqio.pants.node.tasks.webpack import WebPack
from fsqio.pants.node.tasks.webpack_bundle import WebPackBundle
from fsqio.pants.node.tasks.webpack_resolve import WebPackResolve


def build_file_aliases():
  return BuildFileAliases(
    targets={
      'webpack_module': WebPackModule,
    },
  )

def register_goals():

  Goal.by_name('test').uninstall_task('node')
  Goal.by_name('resolve').uninstall_task('node')

  task(name='webpack', action=WebPack).install()
  task(name='webpack-resolve', action=WebPackResolve).install()
  task(name='webpack-bundle', action=WebPackBundle).install('webpack')
