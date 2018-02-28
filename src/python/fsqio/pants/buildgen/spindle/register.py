# coding=utf-8
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import

from pants.goal.task_registrar import TaskRegistrar as task

from fsqio.pants.buildgen.spindle.buildgen_spindle import BuildgenSpindle


def register_goals():

  task(
    name='spindle',
    action=BuildgenSpindle,
  ).install('buildgen')
