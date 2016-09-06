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

import hashlib
import os

from pants.base.build_environment import get_buildroot
from pants.base.fingerprint_strategy import TaskIdentityFingerprintStrategy
from pants.base.workunit import WorkUnitLabel
from pants.contrib.node.tasks.node_paths import NodePaths
from pants.contrib.node.tasks.node_resolve import NodeResolve
from pants.util.dirutil import safe_mkdir

from fsqio.pants.node.subsystems.resolvers.webpack_resolver import WebPackResolver
from fsqio.pants.node.targets.webpack_module import WebPackModule


class WebPackResolveFingerprintStrategy(TaskIdentityFingerprintStrategy):

  def compute_fingerprint(self, target):
    super_fingerprint = super(WebPackResolveFingerprintStrategy, self).compute_fingerprint(target)
    if not isinstance(target, WebPackModule):
      return super_fingerprint
    hasher = hashlib.sha1()
    hasher.update(super_fingerprint)
    hasher.update(target.npm_json)
    with open(os.path.join(get_buildroot(), target.npm_json), 'rb') as f:
      hasher.update(f.read())
    return hasher.hexdigest()


class WebPackResolve(NodeResolve):

  @classmethod
  def implementation_version(cls):
    return super(WebPackResolve, cls).implementation_version() + [('WebPackResolve', 1)]

  @classmethod
  def global_subsystems(cls):
    return super(WebPackResolve, cls).global_subsystems() + (WebPackResolver,)

  @property
  def fingerprint_strategy(self):
    return WebPackResolveFingerprintStrategy

  def cache_target_dirs(self):
    return True

  def execute(self):
    # NOTE(mateo): execute() is _almost_ identical to the superclass except it adds a pluggable fingerprint strategy.
    # TODO(mateo): Get that line upstream and kill this override.
    targets = self.context.targets(predicate=self._can_resolve_target)
    if not targets:
      return

    node_paths = self.context.products.get_data(NodePaths, init_func=NodePaths)

    # We must have copied local sources into place and have node_modules directories in place for
    # internal dependencies before installing dependees, so `topological_order=True` is critical.
    with self.invalidated(targets,
                          fingerprint_strategy=self.fingerprint_strategy(self),
                          topological_order=True,
                          invalidate_dependents=True) as invalidation_check:

      with self.context.new_workunit(name='install', labels=[WorkUnitLabel.MULTITOOL]):
        for vt in invalidation_check.all_vts:
          target = vt.target
          resolver_for_target_type = self._resolver_for_target(target).global_instance()
          results_dir = vt.results_dir
          if not vt.valid:
            safe_mkdir(results_dir, clean=True)
            resolver_for_target_type.resolve_target(self, target, results_dir, node_paths)
          node_paths.resolved(target, results_dir)
