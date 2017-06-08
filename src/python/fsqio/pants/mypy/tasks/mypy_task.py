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

import os
import subprocess

from pants.backend.python.targets.python_binary import PythonBinary
from pants.backend.python.targets.python_library import PythonLibrary
from pants.backend.python.targets.python_tests import PythonTests
from pants.base.exceptions import TaskError
from pants.base.workunit import WorkUnit, WorkUnitLabel
from pants.task.task import Task


class MypyTask(Task):
  """Invoke the mypy static type analzyer for Python."""

  _PYTHON_SOURCE_EXTENSION = '.py'

  @classmethod
  def register_options(cls, register):
    super(MypyTask, cls).register_options(register)
    register('--py3-path', type=str, default='python3',
             help='Path to Python 3 interpreter')

  @classmethod
  def supports_passthru_args(cls):
    return True

  def __init__(self, *args, **kwargs):
    super(MypyTask, self).__init__(*args, **kwargs)

  @staticmethod
  def is_non_synthetic_python_target(target):
    return not target.is_synthetic and isinstance(target, (PythonLibrary, PythonBinary, PythonTests))

  def _calculate_python_sources(self, targets):
    """Generate a set of source files from the given targets."""
    python_eval_targets = filter(self.is_non_synthetic_python_target, targets)
    sources = set()
    for target in python_eval_targets:
      sources.update(
        source for source in target.sources_relative_to_buildroot()
        if os.path.splitext(source)[1] == self._PYTHON_SOURCE_EXTENSION
      )
    return list(sources)

  def execute(self):
    sources = self._calculate_python_sources(self.context.target_roots)
    if not sources:
      self.context.log.debug('No Python sources to check.')
      return

    cmd = [self.get_options().py3_path, '-m', 'mypy', '-2'] + self.get_passthru_args() + sources
    self.context.log.debug('mypy command: {}'.format(' '.join(cmd)))

    with self.context.new_workunit(
      name='check',
      labels=[WorkUnitLabel.TOOL, WorkUnitLabel.RUN],
      log_config=WorkUnit.LogConfig(level=self.get_options().level, colors=self.get_options().colors),
      cmd=' '.join(cmd)) as workunit:
      proc = subprocess.Popen(cmd, stdout=workunit.output('stdout'), stderr=subprocess.STDOUT)
      returncode = proc.wait()
      if returncode != 0:
        raise TaskError('mypy failed: code={}'.format(returncode))
