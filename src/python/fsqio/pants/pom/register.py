# coding=utf-8
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import

import os

from pants.backend.jvm.repository import Repository
from pants.base.build_environment import get_buildroot
from pants.build_graph.build_file_aliases import BuildFileAliases
from pants.goal.goal import Goal
from pants.goal.task_registrar import TaskRegistrar as task

from fsqio.pants.pom.pom_resolve import PomResolve


oss_sonatype_repo = Repository(
  name='oss_sonatype_repo',
  url='https://oss.sonatype.org/#stagingRepositories',
  push_db_basedir=os.path.join(get_buildroot(), 'pushdb'),
)

def build_file_aliases():
  return BuildFileAliases(
    objects={
      'oss_sonatype_repo': oss_sonatype_repo,
    },
  )

def register_goals():
  Goal.by_name('resolve').uninstall_task('ivy')
  task(name='pom-resolve', action=PomResolve).install()
