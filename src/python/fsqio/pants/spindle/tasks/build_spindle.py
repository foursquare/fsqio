# coding=utf-8
# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

from __future__ import (
  absolute_import,
  division,
  generators,
  nested_scopes,
  print_function,
  unicode_literals,
  with_statement,
)

from collections import namedtuple
import os
import shutil
import subprocess

from pants.base.build_environment import get_buildroot
from pants.base.exceptions import TaskError
from pants.base.workunit import WorkUnitLabel
from pants.util.memo import memoized_property

from fsqio.pants.spindle.tasks.spindle_task import SpindleTask
from fsqio.pants.util.dirutil import safe_mkdir


class BuildSpindle(SpindleTask):
  """Compile spindle in a shelled pants invocation before using it for codegen.

  This task is specialized to build just one target - the spindle codegen binary. The spindle binary requires
  spindle to do codegen for itself in order to build. This self-dependency has required this hijack of
  the round engine by shelling into a separate pants invocation.

  This task should have as few side-effects as possible!
  """

  PANTS_SCRIPT_NAME = 'pants'
  PantsResult = namedtuple('PantsResult', ['command', 'returncode'])

  def run_pants_no_lock(self, command, workunit_name=None, **kwargs):
    global_args = ['--quiet'] if self.get_options().quiet else []
    global_args.extend([
      '--no-pantsrc',
      '--no-lock',
    ])
    pants_script = os.path.join(get_buildroot(), self.PANTS_SCRIPT_NAME)
    pants_command = [pants_script] + global_args + command

    with self.context.new_workunit(name=workunit_name, labels=[WorkUnitLabel.RUN]) as workunit:
      proc = subprocess.Popen(
        pants_command,
        stdout=workunit.output('stdout'),
        stderr=workunit.output('stderr'),
        **kwargs
      )
      proc.communicate()
    return self.PantsResult(pants_command, proc.returncode)

  @classmethod
  def register_options(cls, register):
    super(BuildSpindle, cls).register_options(register)
    register(
      '--shelled',
      fingerprint=True,
      advanced=True,
      type=bool,
      default=False,
      help="Don't pass this flag, internal use only!",
    )

  @classmethod
  def product_types(cls):
    return ['spindle_binary']

  @property
  def cache_target_dirs(self):
    return True

  @memoized_property
  def spindle_bundle_out(self):
    return os.path.join(self.get_options().pants_distdir, 'spindle-bundle', 'spindle.jar')

  def execute(self):
    # The 'shelled' option is only passed by this execute method and indicates a shelled run of pants.
    if not self.get_options().shelled:

      # This task is specialized to build just one target - the spindle source.
      targets = [self.spindle_target]
      # TODO: This invalidation is incomplete and should do the stuff done by the jvm_compile fingerprint
      # strategy. But since this task is scheduled to complete before the classpath is resolved, this is tricky.
      with self.invalidated(targets, invalidate_dependents=True) as invalidation_check:
        targets = invalidation_check.all_vts
        if targets and len(targets) != 1:
          raise TaskError("There should only be one versioned target for the build_spindle task!"
                          "(was: {})".format(targets))
        vt = targets[0]
        invalid_vts_by_target = {vt.target: vt}
        if not vt.valid:
          args = ['--build-spindle-shelled', 'bundle', '--bundle-jvm-deployjar']
          args.append(self.get_options().spindle_codegen_binary)
          results = self.run_pants_no_lock(args, workunit_name='spindle-build')

          if results.returncode != 0:
            # Purposefully not returning a message so the error from the shelled run can be surfaced.
            raise TaskError()

          spindle_bundle = self.spindle_bundle_out
          safe_mkdir(vt.results_dir)
          spindle_binary = os.path.join(vt.results_dir, 'spindle-bundle.jar')
          try:
            shutil.copy(spindle_bundle, spindle_binary)
          except Exception as e:
            raise TaskError("Could not copy the spindle binary at {}:\n{}".format(spindle_bundle, e))

        self.context.products.get('spindle_binary').add(vt.target, vt.results_dir).append('spindle-bundle.jar')
