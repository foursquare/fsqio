# coding=utf-8
# Copyright 2018 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function

from pants.backend.docgen.targets.doc import Wiki
from pants.build_graph.build_file_aliases import BuildFileAliases
from pants.goal.goal import Goal
from pants.goal.task_registrar import TaskRegistrar as task

from fsqio.pants.wiki.subsystems.confluence_subsystem import ConfluenceSubsystem
from fsqio.pants.wiki.tasks.confluence_restful_publish import ConfluenceRestfulPublish


def build_file_aliases():
  return BuildFileAliases(
    objects={
      'confluence': Wiki(name='confluence', url_builder=ConfluenceSubsystem.confluence_url_builder)
    },
  )


def register_goals():
  # Remove upstream XRPC-based plugin to reclaim this namespace.
  Goal.by_name('confluence').uninstall_task('confluence')
  task(name='confluence', action=ConfluenceRestfulPublish).install()
