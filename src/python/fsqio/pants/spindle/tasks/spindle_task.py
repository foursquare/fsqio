# coding=utf-8
# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function, unicode_literals

import os

from pants.backend.jvm.targets.jvm_binary import JvmBinary
from pants.backend.jvm.tasks.nailgun_task import NailgunTask
from pants.base.exceptions import TaskError
from pants.build_graph.address import Address
from pants.option.custom_types import target_option
from pants.util.memo import memoized_property

from fsqio.pants.spindle.targets.ssp_template import SspTemplate


class SpindleTask(NailgunTask):
  """A base class to declare and verify options for spindle tasks."""

  class BadDependency(TaskError):
    """Raise when spindle will error due to missing dependencies."""

  @classmethod
  def register_options(cls, register):
    super(SpindleTask, cls).register_options(register)
    register(
      '--spindle-codegen-binary',
      fingerprint=True,
      advanced=True,
      type=target_option,
      help='Use this Spindle source to generate code.',
    )

  @classmethod
  def implementation_version(cls):
    return super(SpindleTask, cls).implementation_version() + [('SpindleTask', 3)]

  @property
  def cache_target_dirs(self):
    return True

  @memoized_property
  def spindle_target(self):
    return self.get_spindle_target(
      'spindle_codegen_binary',
      self.get_options().spindle_codegen_binary,
      JvmBinary,
    )

  def get_ssp_templates(self, template_target):
    if not isinstance(template_target, SspTemplate):
      raise TaskError(
        'Spindle codegen requires being passed templates as SspTemplate targets (was: {})'.format(template_target)
      )
    return os.path.join(template_target.address.spec_path, template_target.entry_point)

  def get_spindle_target(self, option_name, option_value, target_type):
    return self.resolve_target(option_value, target_type)

  def resolve_target(self, spec, target_type):
    build_graph = self.context.build_graph
    address = Address.parse(spec)
    build_graph.inject_address_closure(address)
    target = build_graph.get_target(address)
    if not isinstance(target, target_type):
      raise self.BadDependency(
        '{} must point to a {} target: (was: {})'.format(spec, target_type, target),
      )
    return target
