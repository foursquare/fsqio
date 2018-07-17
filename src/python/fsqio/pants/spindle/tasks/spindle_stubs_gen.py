# coding=utf-8
# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function, unicode_literals

import os
import shutil

from pants.base.exceptions import TaskError
from pants.base.workunit import WorkUnitLabel
from pants.util.contextutil import temporary_dir
from pants.util.dirutil import safe_mkdir
from pants.util.memo import memoized_property

from fsqio.pants.spindle.tasks.spindle_gen import SpindleGen


class SpindleStubsGen(SpindleGen):
  """Generate stub Spindle files for consumption by IDEs."""

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
    register(
      '--skip',
      default=True,
      type=bool,
      help='The stubs will only be generated if this option is False.',
    )

  @classmethod
  def product_types(cls):
    return ['stubs']

  @classmethod
  def prepare(cls, options, round_manager):
    super(SpindleStubsGen, cls).prepare(options, round_manager)
    round_manager.require('spindle_binary')

  @classmethod
  def implementation_version(cls):
    return super(SpindleStubsGen, cls).implementation_version() + [('SpindleStubsGen', 3)]

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
    if self.get_options().skip:
      self.context.log.debug('SKIPPING {} because the skip option is True.'.format(self.options_scope))
      return
    self.context.log.debug('Copying spindle stubs to: {}'.format(self.stubs_out))
    with self.invalidated(
      self.codegen_targets(),
      invalidate_dependents=True,
      fingerprint_strategy=self.get_fingerprint_strategy(),
    ) as invalidation_check:
      with self.context.new_workunit(name='stubs-create', labels=[WorkUnitLabel.MULTITOOL]):
        with temporary_dir() as workdir:
          for vt in invalidation_check.all_vts:
            generated_sources = self.calculate_generated_sources(vt.target)
            if not vt.valid:
              self.execute_codegen(vt.target, vt.results_dir, workdir)
              self.cache_generated_files(generated_sources, self.namespace_out(workdir), vt.results_dir, overwrite=True)

            # Copy the stubs to the output directory consumed by the IDE. We already know that the invalid targets were
            # overwritten to that location, this next call just replaces any missing files in valid targets, in case
            # this is the first run for a user, or if they deleted the output dir for some reason.
            overwrite = not vt.valid
            self.cache_generated_files(generated_sources, vt.results_dir, self.stubs_out, overwrite=overwrite)

  def cache_generated_files(self, generated_files, src, dst, overwrite=False):
    # For the stubs we add an overwrite flag. This is actually safe even in with mainline spindle, since the overwrite
    # is True for invalid targets, which will always fail the isfile check. We skip it for perf reasons.
    for gen_file in generated_files:
      safe_mkdir(os.path.join(dst, os.path.dirname(gen_file)))
      new_path = os.path.join(dst, gen_file)
      if overwrite or not os.path.isfile(new_path):
        shutil.copy2(os.path.join(src, gen_file), new_path)
