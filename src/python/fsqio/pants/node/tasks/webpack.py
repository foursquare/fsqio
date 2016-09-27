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

import os.path
from textwrap import dedent

from pants.backend.codegen.tasks.simple_codegen_task import SimpleCodegenTask
from pants.base.exceptions import TaskError
from pants.base.workunit import WorkUnitLabel
from pants.build_graph.resources import Resources as BaseResources
from pants.contrib.node.tasks.node_paths import NodePaths
from pants.contrib.node.tasks.node_task import NodeTask
from pants.util.contextutil import pushd
from pants.util.dirutil import safe_mkdir

from fsqio.pants.node.targets.webpack_module import WebPackModule


class WebPack(NodeTask, SimpleCodegenTask):
  """Run webpack on WebPackModule targets.

  The result is a synthetic target that subclasses `Resources` and
  the task exports the `compile_classpath` product, so
  the output should appear on the classpath of any
  JVM target that transitively depends on the node target
  being codegenned.

  WARNING: The node module must express a dependency on webpack
  in its package.json / npm-shrinkwrap.json, or this task will
  fail.
  """

  class Resources(BaseResources):
    """Resources container to hold generated json."""

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
    return super(WebPack, cls).implementation_version() + [('WebPack', 3)]

  @classmethod
  def prepare(cls, options, round_manager):
    super(WebPack, cls).prepare(options, round_manager)
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
    # round_manager.require_data(NodePaths)
    round_manager.require_data('webpack_distribution')

  def synthetic_target_type(self, target):
    return WebPack.Resources

  def is_gentarget(self, target):
    return isinstance(target, WebPackModule)

  def execute_codegen(self, target, target_workdir):
    node_paths = self.context.products.get_data(NodePaths)
    if not node_paths:
      raise TaskError("No npm distribution was found!")
    node_path = node_paths.node_path(target)
    dest_dir = os.path.join(target_workdir, 'webpack')
    # NOTE(mateo): The target_workdir is the 'current' symlink and not respected by clean=True. Need to fix upstream.
    safe_mkdir(os.path.realpath(target_workdir), clean=True)

    # Added "bail" to the args since webpack only returns failure on failed transpiling, treating missing deps or
    # syntax errors as soft errors. This resulted in Pants returning success while the canary fails health check.
    args = [
      'run-script',
      'webpack',
      '--',
      '--bail',
      '--output-path={}'.format(dest_dir),
      '--env=dist',
    ]
    with pushd(node_path):
      result, npm_run = self.execute_npm(
        args=args,
        workunit_labels=[WorkUnitLabel.RUN],
      )
      if result:
        raise TaskError(dedent(
          """ webpack command:
          \n\t{} failed with exit code {}
          """
         .format(' '.join(npm_run.cmd), result)
        ))
