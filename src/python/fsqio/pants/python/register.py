# coding=utf-8
# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function, unicode_literals

from pants.build_graph.build_file_aliases import BuildFileAliases
from pants.goal.goal import Goal
from pants.goal.task_registrar import TaskRegistrar as task

from fsqio.pants.python.filtered_python_requirements import FilteredPythonRequirements
from fsqio.pants.python.tasks.futurize_task import FuturizeTask
from fsqio.pants.python.tasks.mypy_task import MypyTask
from fsqio.pants.python.tasks.pytest_prep import PytestPrep
from fsqio.pants.python.tasks.pytest_run import PytestRun


def build_file_aliases():
  return BuildFileAliases(
    context_aware_object_factories={
      'filtered_python_requirements': FilteredPythonRequirements,
    }
  )


def register_goals():
  task(name='mypy', action=MypyTask).install('mypy')
  task(name='futurize', action=FuturizeTask).install('futurize')
  Goal.by_name('test').uninstall_task('pytest-prep')
  task(name='pytest-prep', action=PytestPrep).install('test')
  Goal.by_name('test').uninstall_task('pytest')
  task(name='pytest', action=PytestRun).install('test')
