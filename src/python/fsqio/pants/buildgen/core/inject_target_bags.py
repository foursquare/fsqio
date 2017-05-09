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

import os

from pants.base.build_environment import get_buildroot
from pants.base.exceptions import TaskError
from pants.build_graph.address import Address
from pants.build_graph.target import Target

from fsqio.pants.buildgen.core.buildgen_base import BuildgenBase
from fsqio.pants.buildgen.core.buildgen_target_bag import BuildgenTargetBag
from fsqio.pants.buildgen.core.subsystems.buildgen_subsystem import BuildgenSubsystem


class BuildgenInjectTargetBags(BuildgenBase):
  """Process any BuildgenTargetBags and inject the aggregated targets when constructing the initial build_graph.

  The target bags are populated by buildgen, and generally walk a directory and gathers all target defs
  that match the requested target type. (i.e. all PythonThriftLibrary under src/thrift). These target bags maintain
  the found targets as the bag's dependencies.
  """
  # This is just installed into `test` for now - the compile targets all hold to the actual dependency graph for our
  # existing bags.

  @classmethod
  def implementation_version(cls):
    return super(BuildgenInjectTargetBags, cls).implementation_version() + [('BuildgenInjectTargetBags', 1)]

  @classmethod
  def alternate_target_roots(cls, options, address_mapper, build_graph):
    subsystem = BuildgenSubsystem.Factory.global_instance().create()
    bag_specs = subsystem.buildgen_target_bags

    # Gather any bags that are already in the build_graph, which means those bags depend on a target_root.
    bags = build_graph.targets(lambda x: x.address.spec in bag_specs)
    for bag_target in bags:
      if not isinstance(bag_target, BuildgenTargetBag):
        raise TaskError("The generated_target_bags option must point to a list of BuildgenTargetBag specs.")

      # Construct a unique target address.
      spec_root = os.path.relpath(options.get('pants_workdir'), get_buildroot())
      spec_path = os.path.join(spec_root, subsystem.Factory.options_scope, 'inject_target_bags')
      synthetic_address = Address.parse(os.path.join(spec_path, bag_target.id))

      build_graph.inject_synthetic_target(
        address=synthetic_address,
        target_type=Target,
        dependencies=build_graph.dependencies_of(bag_target.address),
        derived_from=bag_target,
        tags=bag_target.tags,
      )

      for dependent in bag_target.dependents:
        build_graph.inject_dependency(
          dependent=synthetic_address,
          dependency=dependent.address,
        )
      build_graph.maybe_inject_address_closure(bag_target.address)

    # Explicitly returning None only for readabiliy's sake. It means that we are not changing the target_roots
    # themselves. We do inject a synthetic dependency, but not as a full target_roots.
    return None

  def execute(self):
    # This should most properly be a mixin on the Changed tasks. But those are upstream tasks. There are
    # other ways around that, but this is basically fine.
    return
