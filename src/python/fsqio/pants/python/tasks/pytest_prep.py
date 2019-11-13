# coding=utf-8
# Copyright 2019 Foursquare Labs Inc. All Rights Reserved.
# fixlint=skip

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

from pants.backend.python.subsystems.pytest import PyTest
from pants.backend.python.tasks.python_execution_task_base import PythonExecutionTaskBase
from pex.pex_info import PexInfo
import pkg_resources

# This is pretty much a full copy of upstream. We made two changes:
# - We add a `requirements` option to the task
# - We add the contents from that option to the output of `extra_requirements`
# We do this so that we can add extra constraints to pytest dependencies.


class PytestPrep(PythonExecutionTaskBase):
  """Prepares a PEX binary for the current test context with `py.test` as its entry-point."""

  class PytestBinary(object):
    """A `py.test` PEX binary with an embedded default (empty) `pytest.ini` config file."""

    _COVERAGE_PLUGIN_MODULE_NAME = '__{}__'.format(__name__.replace('.', '_'))

    def __init__(self, pex):
      self._pex = pex

    @property
    def pex(self):
      """Return the loose-source py.test binary PEX.

      :rtype: :class:`pex.pex.PEX`
      """
      return self._pex

    @property
    def config_path(self):
      """Return the absolute path of the `pytest.ini` config file in this py.test binary.

      :rtype: str
      """
      return os.path.join(self._pex.path(), 'pytest.ini')

    @classmethod
    def coverage_plugin_module(cls):
      """Return the name of the coverage plugin module embedded in this py.test binary.

      :rtype: str
      """
      return cls._COVERAGE_PLUGIN_MODULE_NAME

  @classmethod
  def register_options(cls, register):
    super(PytestPrep, cls).register_options(register)
    register(
      '--requirements',
      advanced=True,
      fingerprint=True,
      type=list,
      help='Add more constraints to pytest requiements',
    )

  @classmethod
  def implementation_version(cls):
    return super(PytestPrep, cls).implementation_version() + [('PytestPrep', 3)]

  @classmethod
  def product_types(cls):
    return [cls.PytestBinary]

  @classmethod
  def subsystem_dependencies(cls):
    return super(PytestPrep, cls).subsystem_dependencies() + (PyTest,)

  def extra_requirements(self):
    return tuple(self.get_options().requirements) + PyTest.global_instance().get_requirement_strings()

  def extra_files(self):
    yield self.ExtraFile.empty('pytest.ini')
    yield self.ExtraFile(path='{}.py'.format(self.PytestBinary.coverage_plugin_module()),
                         content=pkg_resources.resource_string(__name__, 'coverage/plugin.py'))

  def execute(self):
    pex_info = PexInfo.default()
    pex_info.entry_point = 'pytest'
    pytest_binary = self.create_pex(pex_info)
    self.context.products.register_data(self.PytestBinary, self.PytestBinary(pytest_binary))
