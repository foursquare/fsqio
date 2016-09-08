# coding=utf-8
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import

import os

from pants.backend.jvm.repository import Repository
from pants.base.build_environment import get_buildroot
from pants.build_graph.build_file_aliases import BuildFileAliases
from pants.goal.goal import Goal
from pants.goal.task_registrar import TaskRegistrar as task
from pants.task.task import Task

from fsqio.pants.publish.pom_publish import PomPublish, PomTarget


oss_sonatype_repo = Repository(
  name='oss_sonatype_repo',
  url='https://oss.sonatype.org/#stagingRepositories',
  push_db_basedir=os.path.join(get_buildroot(), 'pushdb'),
)


def build_file_aliases():
  return BuildFileAliases(
    targets={
      'pom_target': PomTarget,
    },
  )


def register_goals():
  # Some legacy libraries have broken javadoc - but the javadoc product is required by pom-publish and publish.jar.
  # This mocks that product and sidesteps the javadoc generation completely. The real fix is to require working
  # javadoc for any published lib - especially things we publish externally like Fsq.io.
  # TODO(mateo): Fix javadoc errors for published libraries and reinstall tasks.
  Goal.by_name('doc').uninstall_task('javadoc')
  Goal.by_name('doc').uninstall_task('scaladoc')

  class MockJavadoc(Task):
    @classmethod
    def product_types(cls):
      return [
        'javadoc', 'scaladoc'
      ]

    def execute(self):
      pass

  task(name='mockdoc', action=MockJavadoc).install('doc')
  task(name='pom-publish', action=PomPublish).install()
