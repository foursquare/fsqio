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

from pants.backend.jvm.targets.jvm_binary import JvmBinary
from pants.base.exceptions import TaskError
from pants.build_graph.address import Address
from pants.option.custom_types import target_list_option
from pants.task.task import Task
from pants.util.memo import memoized_property


class SpindleTask(Task):
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
      type=target_list_option,
      help='Use this Spindle source to generate code.',
    )

  @memoized_property
  def spindle_target(self):
    return self.get_spindle_target('spindle_codegen_binary', self.get_options().spindle_codegen_binary, JvmBinary)

  def get_spindle_target(self, option_name, option_value, target_type):
    self.verify_option(option_name, option_value, target_type)
    return self.resolve_target(option_value[0], target_type)

  def resolve_target(self, spec, target_type):
    build_graph = self.context.build_graph
    address = Address.parse(spec)
    build_graph.inject_address_closure(address)
    target = build_graph.get_target(address)
    if not isinstance(target, target_type):
      raise self.BadDependency('{} must point to a {} target. '
                                         '(was: {})'.format(spec, target_type, target))
    return target

  def verify_option(self, option_name, option_value, required_target_type):
    if not option_value or len(option_value) > 1:
      raise self.BadDependency('The {} option is required to point to exactly '
                               'one {} target. (was {})'.format(option_name, required_target_type, option_value))
