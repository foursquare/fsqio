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

from pants.contrib.node.subsystems.resolvers.npm_resolver import NpmResolver
from pants.contrib.node.tasks.node_resolve import NodeResolve

from fsqio.pants.node.targets.webpack_module import WebPackModule


class WebPackResolver(NpmResolver):
  """Subsystem to resolve the webpack_modules."""
  options_scope = 'webpack-resolver'

  @classmethod
  def register_options(cls, register):
    NodeResolve.register_resolver_for_type(WebPackModule, cls)
    super(WebPackResolver, cls).register_options(register)

  def _emit_package_descriptor(self, node_task, target, results_dir, node_paths):
    # Upstream uses BUILD files to generate the package.json. I might be interested in trying that sometime.
    pass
