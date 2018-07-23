# coding=utf-8
# Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function

from pants.goal.task_registrar import TaskRegistrar as task
from pants.task.task import Task

from fsqio.pants.tags.validate import Tagger, Validate


def register_goals():
  task(name='tag', action=Tagger).install()
  task(name='validate', action=Validate).install()

  class ForceValidation(Task):
    @classmethod
    def prepare(cls, options, round_manager):
      round_manager.require_data('validated_build_graph')

    def execute(self):
      pass

  task(name='validate-graph', action=ForceValidation).install('gen')
