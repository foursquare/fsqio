# coding=utf-8
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import

from pants.goal.task_registrar import TaskRegistrar as task

from fsqio.pants.buildgen.python.buildgen_python import BuildgenPython
from fsqio.pants.buildgen.python.map_python_exported_symbols import MapPythonExportedSymbols


def register_goals():

  task(
    name='map-python-exported-symbols',
    action=MapPythonExportedSymbols,
  ).install()

  task(
    name='python',
    action=BuildgenPython,
  ).install('buildgen')
