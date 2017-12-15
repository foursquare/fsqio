# coding=utf-8
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import

from pants.goal.goal import Goal
from pants.goal.task_registrar import TaskRegistrar as task

from fsqio.pants.pom_resolve.pom_resolve import PomResolve


def register_goals():
  Goal.by_name('resolve').uninstall_task('ivy')
  task(name='pom-resolve', action=PomResolve).install()
