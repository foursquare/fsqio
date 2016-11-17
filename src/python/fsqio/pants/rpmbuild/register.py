# coding=utf-8
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

from __future__ import (
  absolute_import,
  division,
  generators,
  nested_scopes,
  print_function,
  unicode_literals,
  with_statement,
)

from pants.build_graph.build_file_aliases import BuildFileAliases
from pants.goal.task_registrar import TaskRegistrar as task

from fsqio.pants.rpmbuild.targets.rpm_spec import RpmSpecTarget
from fsqio.pants.rpmbuild.tasks.rpmbuild_task import RpmbuildTask


def build_file_aliases():
  return BuildFileAliases(
    targets={
      RpmSpecTarget.alias(): RpmSpecTarget,
    }
  )


def register_goals():
  task(name='rpmbuild', action=RpmbuildTask).install('rpmbuild')
