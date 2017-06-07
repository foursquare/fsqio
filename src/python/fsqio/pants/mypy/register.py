# coding=utf-8
# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

from __future__ import (
  absolute_import,
  division,
  generators,
  nested_scopes,
  print_function,
  unicode_literals,
  with_statement,
)

from pants.goal.task_registrar import TaskRegistrar as task

from fsqio.pants.mypy.tasks.mypy_task import MypyTask


def register_goals():
  task(name='mypy', action=MypyTask).install('mypy')
