# coding=utf-8
# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function, unicode_literals

import os

from builtins import filter
from pants.backend.python.interpreter_cache import PythonInterpreterCache
from pants.backend.python.subsystems.python_setup import PythonSetup
from pants.backend.python.targets.python_binary import PythonBinary
from pants.backend.python.targets.python_library import PythonLibrary
from pants.backend.python.targets.python_target import PythonTarget
from pants.backend.python.targets.python_tests import PythonTests
from pants.backend.python.tasks.resolve_requirements_task_base import ResolveRequirementsTaskBase
from pants.base.exceptions import TaskError
from pants.base.workunit import WorkUnit, WorkUnitLabel
from pants.python.python_repos import PythonRepos
from pants.util.memo import memoized_property
from pants.util.process_handler import subprocess
from typing import List, Optional, Set


class FuturizeTask(ResolveRequirementsTaskBase):
  """Invoke the futurize tool for Python."""

  _PYTHON_SOURCE_EXTENSION = '.py'

  def __init__(self, *args, **kwargs):
    super(FuturizeTask, self).__init__(*args, **kwargs)

  @classmethod
  def register_options(cls, register):
    super(FuturizeTask, cls).register_options(register)
    register('--check', type=bool, default=False,
             help='Determines if Pants stops due to error.')
    register('--stage', type=int, default=1,
             help='Stage of transformation (1 or 2) see futurize docs for more info')
    register('--only', type=str, default=None)

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

  def _calculate_python_sources(self, targets, tag):
    # type: (List[PythonTarget], Optional[str]) -> List[str]
    """Generate a set of source files from the given targets."""
    python_eval_targets = list(filter(self.is_non_synthetic_python_target, targets))
    sources = set()  # type: Set[str]
    for target in python_eval_targets:
      if not tag or tag in target.tags:
        sources.update(
          source for source in target.sources_relative_to_buildroot()
          if os.path.splitext(source)[1] == self._PYTHON_SOURCE_EXTENSION
        )
    return list(sources)

  def execute(self):
    # type: () -> None
    opts = self.get_options()
    tag = opts.only

    sources = self._calculate_python_sources(self.context.target_roots, tag)
    if not sources:
      self.context.log.debug('No Python sources to check.')
      return

    futurize_opts = [
      '-j8',
    ]

    if opts.stage in (0, 1, 2):
      futurize_opts.append('-{}'.format(opts.stage))
    else:
      raise TaskError('--stage can only have a value of 0, 1 or 2, not {}'.format(opts.stage))

    if opts.stage in (0, 2):
      futurize_opts.extend(['-p', '-u'])

    if not opts.check:
      futurize_opts.extend(['-w', '-n', '--no-diff'])

    cmd = ['.pvenvs/fs3/bin/futurize'] + futurize_opts + self.get_passthru_args() + sources
    self.context.log.debug('futurize command: {}'.format(' '.join(cmd)))

    with self.context.new_workunit(
      name='check' if opts.check else 'apply',
      labels=[WorkUnitLabel.TOOL, WorkUnitLabel.RUN],
      log_config=WorkUnit.LogConfig(level=self.get_options().level, colors=self.get_options().colors),
      cmd=' '.join(cmd)) as workunit:
      proc = subprocess.Popen(cmd, stdout=workunit.output('stdout'), stderr=subprocess.PIPE)
      _, stderr = proc.communicate()
      refactor_count = stderr.count('RefactoringTool: Refactored')

      if not opts.check:
        workunit.output('stdout').write('Refactored {} source files.'.format(refactor_count))

    if proc.returncode != 0:
      raise TaskError('futurize failed: code={}'.format(proc.returncode))

    if opts.check and refactor_count:
      raise TaskError('futurize would have applied changes to {} files.'.format(refactor_count))
