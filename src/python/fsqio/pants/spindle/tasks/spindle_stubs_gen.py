# coding=utf-8
# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

from __future__ import (
  absolute_import,
  division,
  generators,
  nested_scopes,
  print_function,
  unicode_literals,
  with_statement,
)

import shutil

from pants.base.exceptions import TaskError
from pants.util.dirutil import safe_rmtree
from pants.util.memo import memoized_property

from fsqio.pants.spindle.tasks.spindle_gen import SpindleGen


class SpindleStubsGen(SpindleGen):
  """Generate stub Spindle files for consumption by IDEs."""

  @classmethod
  def product_types(cls):
    return ['spindle_stubs']

  @classmethod
  def register_options(cls, register):
    super(SpindleStubsGen, cls).register_options(register)
    register(
      '--stub-output-path',
      fingerprint=False,
      advanced=True,
      type=str,
      help='Overrides the output path for spindle record generation.',
    )

  @memoized_property
  def java_template(self):
    # Do not generate java stubs since they aren't used by the IDE.
    return None

  @memoized_property
  def stubs_out(self):
    outpath = self.get_options().stub_output_path
    if not outpath:
      raise TaskError("For stub generation, stub_output_path argument is required")
    return outpath

  def execute(self):
    # This is an abuse. But it is a quick way to tack on an extra copy and I revisit when/if we rework spindle_gen.
    super(SpindleStubsGen, self).execute()

    self.context.log.debug('Copying spindle stubs to: {}'.format(self.stubs_out))
    safe_rmtree(self.stubs_out)
    shutil.copytree(self.workdir, self.stubs_out)

  def _additional_generated_sources(self, target):
    return None
