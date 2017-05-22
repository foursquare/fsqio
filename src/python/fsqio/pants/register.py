# coding=utf-8
# Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import

from pants.contrib.go.tasks.go_buildgen import GoBuildgen
from pants.goal.goal import Goal
from pants.goal.task_registrar import TaskRegistrar as task
from pants.task.task import Task

from fsqio.pants.validate import Tagger, Validate


def register_goals():
  task(name='tag', action=Tagger).install()
  task(name='validate', action=Validate).install()

  class ForceValidation(Task):
    @classmethod
    def prepare(cls, options, round_manager):
      round_manager.require_data('validated_build_graph')

    def execute(self):
      pass

  Goal.register('bg', 'Buildgen scoped to only Go.')

  Goal.by_name('compile').uninstall_task('jvm-dep-check')
  Goal.by_name('dep-usage').uninstall_task('jvm')
  task(name='validate-graph', action=ForceValidation).install('gen')
  task(name='go', action=GoBuildgen).install('bg')
  Goal.by_name('buildgen').uninstall_task('go')
