# coding=utf-8
# Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import

import os

from pants.backend.jvm.repository import Repository
from pants.base.build_environment import get_buildroot
from pants.build_graph.build_file_aliases import BuildFileAliases
from pants.goal.goal import Goal
from pants.goal.task_registrar import TaskRegistrar as task

from fsqio.pants.pom.ivy_diff import PomIvyDiff
from fsqio.pants.pom.pom_publish import PomPublish, PomTarget
from fsqio.pants.pom.pom_resolve import PomResolve
from fsqio.pants.spindle.targets.spindle_thrift_library import SpindleThriftLibrary
from fsqio.pants.spindle.targets.ssp_template import SspTemplate
from fsqio.pants.spindle.tasks.build_spindle import BuildSpindle
from fsqio.pants.spindle.tasks.spindle_gen import SpindleGen


internal_repo = Repository(
  name = 'dummy_repo',
  url = 'http://www.example.com',
  push_db_basedir = os.path.join(
    get_buildroot(),
    'pushdb',
  ),
)

def build_file_aliases():
  return BuildFileAliases(
    targets={
      'spindle_thrift_library': SpindleThriftLibrary,
      'scala_record_library': SpindleThriftLibrary,
      'ssp_template': SspTemplate,
      'pom_target': PomTarget,
    },
    objects={
      'internal_repo': internal_repo,
    },
  )

def register_goals():


  Goal.by_name('resolve').uninstall_task('ivy')
  task(name='pom-resolve', action=PomResolve).install()
  task(name='pom-ivy-diff', action=PomIvyDiff).install()
  task(name='pom-publish', action=PomPublish).install()

  task(name='build-spindle', action=BuildSpindle).install()
  task(name='spindle', action=SpindleGen).install('gen')


