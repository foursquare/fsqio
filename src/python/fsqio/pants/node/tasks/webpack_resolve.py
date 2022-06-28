# coding=utf-8
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function, unicode_literals

import hashlib
import logging
import os

from pants.base.build_environment import get_buildroot
from pants.base.fingerprint_strategy import DefaultFingerprintStrategy
from pants.base.payload import Payload
from pants.base.payload_field import PrimitiveField
from pants.build_graph.address import Address
from pants.build_graph.target import Target
from pants.contrib.node.tasks.node_paths import NodePaths
from pants.contrib.node.tasks.node_resolve import NodeResolve
from pants.option.custom_types import file_option
from pants.util.memo import memoized_method, memoized_property

from fsqio.pants.node.subsystems.resolvers.webpack_resolver import WebPackResolver
from fsqio.pants.node.subsystems.webpack_distribution import WebPackDistribution
from fsqio.pants.node.targets.webpack_module import WebPackModule


logger = logging.getLogger(__name__)


class WebPackResolveFingerprintStrategy(DefaultFingerprintStrategy):

  def compute_fingerprint(self, target):
    # TODO(mateo): Needs to mixin the node distribution from upstream node tests.
    super_fingerprint = super(WebPackResolveFingerprintStrategy, self).compute_fingerprint(target)
    if not isinstance(target, WebPackModule):
      return super_fingerprint
    hasher = hashlib.sha1()
    hasher.update(super_fingerprint)
    hasher.update(target.npm_json)
    with open(os.path.join(get_buildroot(), target.npm_json), 'rb') as f:
      hasher.update(f.read())
    return hasher.hexdigest()


class ResolvedWebPackDistribution(Target):

   def __init__(self, distribution_fingerprint=None, *args, **kwargs):
    """Synthetic target that represents a resolved webpack distribution."""
    # Creating the synthetic target lets us avoid any special casing in regards to build order or cache invalidation.
    payload = Payload()
    payload.add_fields({
      'distribution_fingerprint': PrimitiveField(distribution_fingerprint),
    })
    super(ResolvedWebPackDistribution, self).__init__(payload=payload, *args, **kwargs)


class WebPackResolve(NodeResolve):

  @classmethod
  def register_options(cls, register):
    super(WebPackResolve, cls).register_options(register)
    register(
      '--userconfig',
      advanced=True,
      type=file_option,
      fingerprint=True,
      help='Path to npmrc userconfig file. If unset, falls to npm default.',
    )

  @classmethod
  def implementation_version(cls):
    return super(WebPackResolve, cls).implementation_version() + [('WebPackResolve', 7.4)]

  @classmethod
  def subsystem_dependencies(cls):
    return super(WebPackResolve, cls).subsystem_dependencies() + (WebPackResolver, WebPackDistribution,)

  @memoized_property
  def webpack_subsystem(self):
    return WebPackDistribution.global_instance()

  @classmethod
  def prepare(cls, options, round_manager):
    # This purposefully clobbers the super class prepare(), because it registers the products of every Resolver
    # subsystem, and that causes a cycle with Webpack tasks that want to add to the compile_classpath.
    # pylint: disable=no-member
    WebPackResolver.prepare(options, round_manager)

  @classmethod
  def product_types(cls):
    return ['webpack_distribution', NodePaths]

  @memoized_method
  def get_npm_options(self):
    userconfig = self.get_options().userconfig
    return ['--userconfig={}'.format(userconfig)] if userconfig else []

  def cache_target_dirs(self):
    return True

  # NOTE(mateo): Override from NodeTask to allow us to pass through custom npm args.
  def install_module(
    self, target=None, package_manager=None,
    install_optional=False, production_only=False, force=False,
    node_paths=None, workunit_name=None, workunit_labels=None):
    """Installs node module using requested package_manager."""
    package_manager = package_manager or self.node_distribution.get_package_manager(package_manager=package_manager)
    module_args = package_manager._get_installation_args(
      install_optional=install_optional,
      production_only=production_only,
      force=force,
      frozen_lockfile=None,
    )
    npm_options = self.get_npm_options()
    args = list(npm_options + module_args)
    command = package_manager.run_command(args=args, node_paths=node_paths)
    return self._execute_command(
      command, workunit_name=workunit_name, workunit_labels=workunit_labels)

  def execute(self):
    targets = self.context.targets(predicate=self._can_resolve_target)
    if not targets:
      return

    node_paths = self.context.products.get_data(NodePaths, init_func=NodePaths)
    invalidation_context = self.invalidated(
      targets,
      fingerprint_strategy=WebPackResolveFingerprintStrategy(),
      topological_order=True,
      invalidate_dependents=True,
    )
    with invalidation_context as invalidation_check:
      webpack_distribution_target = self.create_synthetic_target(self.fingerprint)
      build_graph = self.context.build_graph
      for vt in invalidation_check.all_vts:
        if not vt.valid:
          resolver_for_target_type = self._resolver_for_target(vt.target).global_instance()

          resolver_for_target_type.resolve_target(self, vt.target, vt.results_dir, node_paths)
        node_paths.resolved(vt.target, vt.results_dir)
        build_graph.inject_dependency(
          dependent=vt.target.address,
          dependency=webpack_distribution_target.address,
        )

  def create_synthetic_target(self, global_fingerprint):
    """Return a synthetic target that represents the resolved webpack distribution."""
    spec_path = os.path.join(os.path.relpath(self.workdir, get_buildroot()))
    name = "webpack-distribution-{}".format(global_fingerprint)
    address = Address(spec_path=spec_path, target_name=name)
    logger.debug("Adding synthetic ResolvedWebPackDistribution target: {}".format(name))
    new_target = self.context.add_new_target(
      address,
      ResolvedWebPackDistribution,
      distribution_fingerprint=global_fingerprint
    )
    return new_target
