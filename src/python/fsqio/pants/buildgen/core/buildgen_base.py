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

from pants.task.task import Task
from pants.util.memo import memoized_property

from fsqio.pants.buildgen.core.subsystems.buildgen_subsystem import BuildgenSubsystem


class BuildgenBase(Task):
  """"A base task that provides the buildgen subsystem to its implementers."""

  @classmethod
  def global_subsystems(cls):
    return super(BuildgenBase, cls).global_subsystems() + (BuildgenSubsystem.Factory,)

  @memoized_property
  def buildgen_subsystem(self):
    return BuildgenSubsystem.Factory.global_instance().create()
