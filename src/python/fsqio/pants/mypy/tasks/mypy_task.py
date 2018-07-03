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
from textwrap import dedent

from pants.backend.python.targets.python_binary import PythonBinary
from pants.backend.python.targets.python_library import PythonLibrary, PythonTarget
from pants.backend.python.targets.python_tests import PythonTests
from pants.base.build_environment import get_buildroot
from pants.base.exceptions import TaskError
from pants.base.workunit import WorkUnit, WorkUnitLabel
from pants.task.task import Task
from typing import List, Set, Text


class MypyTask(Task):
  """Invoke the mypy static type analyzer for Python."""

  _PYTHON_SOURCE_EXTENSION = '.py'

  @classmethod
  def register_options(cls, register):
    super(MypyTask, cls).register_options(register)
    register('--py3-path', type=str, default='python3',
             help='Path to Python 3 interpreter')
    register('--warning-only', type=bool, default=True,
             help='Determines if Pants stops due to error.')
    register('--ignore-missing-imports', type=bool, default=True,
             help='Ignore missing type stubs if not found. Useful for mostly untyped code bases.')
    register('--config-file', type=str, default='build-support/fsqio/mypy/mypy.ini',
             help='Path to .ini file, relative to build root.')

  @classmethod
  def supports_passthru_args(cls):
    return True

  def __init__(self, *args, **kwargs):
    super(MypyTask, self).__init__(*args, **kwargs)

  @staticmethod
  def is_non_synthetic_python_target(target):
    # type: (PythonTarget) -> bool
    return not target.is_synthetic and isinstance(target, (PythonLibrary, PythonBinary, PythonTests))

  def _calculate_python_sources(self, targets):
    # type: (List[PythonTarget]) -> List[str]
    """Generate a set of source files from the given targets."""
    python_eval_targets = filter(self.is_non_synthetic_python_target, targets)
    sources = set()  # type: Set[str]
    for target in python_eval_targets:
      sources.update(
        source for source in target.sources_relative_to_buildroot()
        if os.path.splitext(source)[1] == self._PYTHON_SOURCE_EXTENSION
      )
    return list(sources)

  # TODO(earellano): users shouldn't have to install Python3 and MyPy locally. This is a temporary solution
  def _assert_mypy_and_python3_installed_locally(self):
    # type: () -> None
    def program_exists_on_path(program_name):  # see https://github.com/pydanny/whichcraft/blob/master/whichcraft.py
      # type: (Text) -> bool
      path = os.environ.get("PATH", os.defpath)  # type: ignore
      path_list = path.split(os.pathsep)
      return any(os.path.exists(os.path.join(path_dir, program_name))
                 for path_dir in path_list)

    def raise_not_installed_error(program_name, install_command):
      # type: (Text, Text) -> None
      raise TaskError(dedent('''
        {0} not found on local path. Install with `{1}`.\n
        (Note this is a temporary solution - Pants will soon automatically install this for you.)
        '''.format(program_name, install_command)))

    py3_interpreter = self.get_options().py3_path
    if not program_exists_on_path(py3_interpreter):
      raise_not_installed_error('python3', 'brew install python3')
    if not program_exists_on_path('mypy'):
      raise_not_installed_error('mypy', 'pip3 install mypy')

  def execute(self):
    # type: () -> None
    self._assert_mypy_and_python3_installed_locally()

    py3_interpreter = self.get_options().py3_path

    sources = self._calculate_python_sources(self.context.target_roots)
    if not sources:
      self.context.log.debug('No Python sources to check.')
      return

    mypy_options = [
      '--py2',  # run in Python2 mode
      '--config-file={}'.format(os.path.join(get_buildroot(), self.get_options().config_file))
    ]
    if self.get_options().ignore_missing_imports:
      mypy_options.append('--ignore-missing-imports')

    cmd = [py3_interpreter, '-m', 'mypy'] + mypy_options + self.get_passthru_args() + sources
    self.context.log.debug('mypy command: {}'.format(' '.join(cmd)))

    with self.context.new_workunit(
      name='check',
      labels=[WorkUnitLabel.TOOL, WorkUnitLabel.RUN],
      log_config=WorkUnit.LogConfig(level=self.get_options().level, colors=self.get_options().colors),
      cmd=' '.join(cmd)) as workunit:
      proc = subprocess.Popen(cmd, stdout=workunit.output('stdout'), stderr=subprocess.STDOUT)
      return_code = proc.wait()
      if not self.get_options().warning_only and return_code != 0:
        raise TaskError('mypy failed: code={}'.format(return_code))
