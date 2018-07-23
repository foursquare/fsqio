# coding=utf-8
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function

from pants.build_graph.build_file_aliases import BuildFileAliases
from pants.goal.goal import Goal
from pants.goal.task_registrar import TaskRegistrar as task

from fsqio.pants.spindle.targets.spindle_thrift_library import SpindleThriftLibrary
from fsqio.pants.spindle.targets.ssp_template import SspTemplate
from fsqio.pants.spindle.tasks.build_spindle import BuildSpindle
from fsqio.pants.spindle.tasks.spindle_gen import SpindleGen
from fsqio.pants.spindle.tasks.spindle_stubs_gen import SpindleStubsGen


def build_file_aliases():
  return BuildFileAliases(
    targets={
      'spindle_thrift_library': SpindleThriftLibrary,
      'scala_record_library': SpindleThriftLibrary,
      'ssp_template': SspTemplate,
    },
  )


def register_goals():
  # Spindle has a circular dependency, in that Spindle is required to build Spindle :(
  # The build_spindle task bootstraps a Spindle binary in order to allow sane iteration on the Spindle source code.
  task(name='build-spindle', action=BuildSpindle).install()
  task(name='spindle', action=SpindleGen).install('gen')

  Goal.register('ide-gen', 'Generate stub interfaces for IDE integration.')
  task(name='spindle-stubs', action=SpindleStubsGen).install('ide-gen')
