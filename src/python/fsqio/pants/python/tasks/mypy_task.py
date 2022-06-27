# coding=utf-8
# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function, unicode_literals

import os

from pants.backend.python.interpreter_cache import PythonInterpreterCache
from pants.backend.python.subsystems.python_setup import PythonSetup
from pants.backend.python.targets.python_binary import PythonBinary
from pants.backend.python.targets.python_library import PythonLibrary
from pants.backend.python.targets.python_target import PythonTarget
from pants.backend.python.targets.python_tests import PythonTests
from pants.backend.python.tasks.resolve_requirements_task_base import ResolveRequirementsTaskBase
from pants.base.exceptions import TaskError
from pants.base.workunit import WorkUnit, WorkUnitLabel
from pants.option.custom_types import file_option
from pants.python.python_repos import PythonRepos
from pants.util.memo import memoized_property
from pants.util.process_handler import subprocess
from typing import List, Set


class MypyTask(ResolveRequirementsTaskBase):
  """Invoke the mypy static type analyzer for Python."""

  _PYTHON_SOURCE_EXTENSION = '.py'

  def __init__(self, *args, **kwargs):
    super(MypyTask, self).__init__(*args, **kwargs)

  @classmethod
  def register_options(cls, register):
    super(MypyTask, cls).register_options(register)
    register('--py3-path', type=str, default='python3',
             help='Path to Python 3 interpreter')
    register('--warning-only', type=bool, default=True,
             help='Determines if Pants stops due to error.')
    register('--ignore-missing-imports', type=bool, default=True,
             help='Ignore missing type stubs if not found. Useful for mostly untyped code bases.')
    register('--site-packages', type=bool, default=False,
             help='Enable searching for PEP 561 compliant packages.')
    register('--config-file', type=file_option, fingerprint=True,
             help='Path to MyPy config (in ini format).')

  @memoized_property
  def _interpreter_cache(self):
    return PythonInterpreterCache(
      PythonSetup.global_instance(),
      PythonRepos.global_instance(),
      logger=self.context.log.debug
    )

  @classmethod
  def supports_passthru_args(cls):
    return True

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

  def execute(self):
    # type: () -> None

    py3_interpreter = self.get_options().py3_path
    if not os.path.isfile(py3_interpreter):
      raise TaskError('Unable to find a Python 3.x interpreter (required for MyPy).')

    sources = self._calculate_python_sources(self.context.target_roots)
    if not sources:
      self.context.log.debug('No Python sources to check.')
      return

    # Determine interpreter used by the sources so we can tell MyPy.
    interpreter_for_targets = self._interpreter_cache.select_interpreter_for_targets(self.context.target_roots)
    target_python_version = interpreter_for_targets.identity.python
    if not interpreter_for_targets:
      raise TaskError('No Python interpreter compatible with specified sources.')

    mypy_options = [
      '--python-version={}'.format(target_python_version),
    ]

    if self.get_options().config_file:
      mypy_options.append('--config-file={}'.format(self.get_options().config_file))

    if self.get_options().ignore_missing_imports:
      mypy_options.append('--ignore-missing-imports')

    if not self.get_options().site_packages:
      mypy_options.append('--no-site-packages')

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
