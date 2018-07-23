# coding=utf-8
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function

from pants.goal.goal import Goal
from pants.goal.task_registrar import TaskRegistrar as task

from fsqio.pants.export.export_filtered import GenStubsAndExport


def register_goals():
  Goal.by_name('export').uninstall_task('export')
  task(
    name='export',
    action=GenStubsAndExport,
  ).install()
