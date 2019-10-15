# coding=utf-8
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function, unicode_literals

from pants.contrib.node.targets.node_module import NodeModule
from pants.contrib.node.targets.node_test import NodeTest
from pants.contrib.node.tasks.node_test import NodeTest as NodeTestRun


class WebPackTest(NodeTest):
  """Dummy target to get around upstream type check."""


class WebPackTestRun(NodeTestRun):
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
    # round_manager.require_data(NodePaths)
    round_manager.require_data('webpack_distribution')

  @classmethod
  def is_node_module(cls, target):
    """Returns `True` if the given target is a `NodeModule`."""
    return isinstance(target, NodeModule) or issubclass(target, NodeModule)

  @classmethod
  def is_node_test(cls, target):
    """Returns `True` if the given target is a `NodeTest`."""
    return isinstance(target, WebPackTest)

  def _validate_target(self, target):
    """Implements abstract TestRunnerTaskMixin._validate_target."""
    if len(target.dependencies) != 2 or not self.is_node_module(target.dependencies[0]):
      message = 'WebPackTest targets must depend on exactly one WebPackModule target.'
      raise Exception(target, message)
