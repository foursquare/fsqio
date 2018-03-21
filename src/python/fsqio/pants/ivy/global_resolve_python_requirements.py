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

from pants.backend.python.tasks2.pex_build_util import has_python_requirements
from pants.backend.python.tasks2.resolve_requirements_task_base import ResolveRequirementsTaskBase
from pants.base.payload_field import PythonRequirementsField
from pants.util.memo import memoized_property

from fsqio.pants.ivy.target_bag_mixin import TargetBagMixin


class GlobalResolvePythonRequirements(TargetBagMixin, ResolveRequirementsTaskBase):
  """Force all python requirements found under configured roots to be brought into context and aggregared into a pex."""
  # NOTE: This is not currently enabled and I have very deep doubts about whether it is useful at all.
  # Keeping here for now as we audit the adoption of the new Python backend from upstream Pants.

  REQUIREMENTS_PEX = 'python_requirements_pex'
  SYNTHETIC_TARGET_NAME = 'global_python_bag'

  @memoized_property
  def bag_target_closure(self):
    return self.context.build_graph.get_target(self.get_synthetic_address()).closure()

  @classmethod
  def product_types(cls):
    return [cls.REQUIREMENTS_PEX]

  @classmethod
  def injected_target_name(cls):
    return cls.SYNTHETIC_TARGET_NAME

  @classmethod
  def gathered_target_type_aliases(cls):
    return ('python_requirement_library',)

  @classmethod
  def add_payload_fields(cls, build_graph, addresses, payload):
    python_reqs = set()
    for address in addresses:
      python_dependency = build_graph.get_target(address)
      python_reqs.update({str(r) for r in python_dependency.requirements})
    payload.add_fields({
      'requirements': PythonRequirementsField(list(python_reqs) or []),
    })
    return payload

  def execute(self):
    inject_reqs = self.context.build_graph.get_target(self.get_synthetic_address()).closure()
    all_targets = set(self.context.targets() | inject_reqs)
    req_libs = self.context.targets(has_python_requirements)
    pex = self.resolve_requirements(req_libs)
    self.context.products.register_data(self.REQUIREMENTS_PEX, pex)
