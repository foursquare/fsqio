# coding=utf-8
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function, unicode_literals

import os.path

from pants.base.exceptions import TaskError
from pants.base.workunit import WorkUnitLabel
from pants.build_graph.resources import Resources as BaseResources
from pants.contrib.node.tasks.node_paths import NodePaths
from pants.contrib.node.tasks.node_task import NodeTask
from pants.task.simple_codegen_task import SimpleCodegenTask
from pants.util.contextutil import pushd
from pants.util.memo import memoized_property

from fsqio.pants.node.subsystems.webpack_distribution import WebPackDistribution
from fsqio.pants.node.targets.webpack_module import NpmResource, WebPackModule


# TODO(mateo): This backend is not bringing any value, should move on to the now-developed upstream contrib.node.
class WebPack(NodeTask, SimpleCodegenTask):
  """Run webpack on WebPackModule targets.

  The result is a synthetic target that subclasses `Resources` and
  the task exports the `compile_classpath` product, so
  the output should appear on the classpath of any
  JVM target that transitively depends on the node target
  being codegenned.

  WARNING: The node module must express a dependency on webpack
  in its package.json / package-lock.json, or this task will
  fail.
  """

  class Resources(BaseResources):
    """Resources container to hold generated json."""

  @classmethod
  def subsystem_dependencies(cls):
    return super(WebPack, cls).subsystem_dependencies() + (WebPackDistribution,)

  @memoized_property
  def webpack_subsystem(self):
    return WebPackDistribution.global_instance()

  @classmethod
  def product_types(cls):
    return super(WebPack, cls).product_types() + [WebPack.Resources, 'compile_classpath']

  @property
  def cache_target_dirs(self):
    return True

  @property
  def _copy_target_attributes(self):
    # Override from SimpleCodegenTask, which expects targets to have a 'provided' attribute.
    # NOTE(Mateo): This is needed for compatability with Pants 1.1.0.
    return ['tags', 'scope']

  @classmethod
  def implementation_version(cls):
    return super(WebPack, cls).implementation_version() + [('WebPack', 6.2)]

  @classmethod
  def prepare(cls, options, round_manager):
    # NOTE(mateo): This task should be requiring the NodePaths product - but doing so results in a goal cycle upstream.
    #  - NodePaths is a product of the NodeResolve task, so requiring it meant Webpack depended on NodeResolve.
    #  - NodeResolve was installed into the 'resolve' goal, and 'resolve' depends on 'gen' goal
    #  - WebPack is a SimpleCodegen subclass, so this meant that WebpackResolve depended on WebPack, obviously a cycle.
    #  - NodeResolve also registers the product requirements of every Resolver subsystem, including ScalaJs, etc.
    #
    # The workaround is simply to not require NodePaths and instead enforce the WebPack -> WebPackResolve with a
    # separate product. NodePaths is just a cache to make sure that a target is not processed by multiple resolvers.
    # We are forcing this to run right before gen, so the upstream resolvers will by definition not have ran.
    #
    # TODO(mateo): Fix the scheduling - it will likely require upstream changes to the Node plugin or forking NodePaths.
    # super(Webpack, cls).prepare(options, round_manager)
    # round_manager.require_data(NodePaths)
    round_manager.require_data('webpack_distribution')

  @classmethod
  def register_options(cls, register):
    super(WebPack, cls).register_options(register)
    register(
      '--destination-dir', type=str, advanced=True, default='webpack',
      help='The directory prefix for webpack resources to go in'
    )
    register(
      '--env',
      type=str,
      advanced=False,
      fingerprint=True,
      default='dev',
      help='The --env=value passed to the webpack command'
    )

  def synthetic_target_type(self, target):
    return WebPack.Resources

  def is_gentarget(self, target):
    return isinstance(target, WebPackModule) and not isinstance(target, NpmResource)

  def execute_codegen(self, target, target_workdir):
    node_paths = self.context.products.get_data(NodePaths)
    if not node_paths:
      raise TaskError("No npm distribution was found!")
    dest_dir = os.path.join(target_workdir, self.get_options().destination_dir, target.name)
    # Added "bail" to the args since webpack only returns failure on failed transpiling, treating missing deps or
    # syntax errors as soft errors. This resulted in Pants returning success while the canary fails health check.
    webpack_args = [
      '--bail',
      '--output-path={}'.format(dest_dir),
      '--env={}'.format(self.get_options().env),
    ]
    args = list(self.webpack_subsystem.get_distribution_args() + webpack_args)

    with pushd(node_paths.node_path(target)):
      result, command = self.run_script(
        'webpack',
        target=target,
        script_args=args,
        node_paths=node_paths.all_node_paths,
        workunit_name=target.address.reference(),
        workunit_labels=[WorkUnitLabel.RUN])
      if result != 0:
        raise TaskError('Run script failed:\n'
                        '\t{} failed with exit code {}'.format(command, result))
