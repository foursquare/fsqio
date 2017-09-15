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

from pants.task.task import Task
from pants.util.dirutil import relative_symlink, rm_rf, safe_mkdir, safe_rmtree

from fsqio.pants.rpmbuild.subsystems.remote_source_fetcher import RemoteSourceFetcher
from fsqio.pants.rpmbuild.targets.remote_source import RemoteSource


class RemoteSourceTask(Task):
  """Fetch versioned files from a URL.

  Each target will have a stable symlink path suitable for runtime consumption later in the graph.
  """

  @staticmethod
  def _is_remote(target):
    return isinstance(target, RemoteSource)

  @classmethod
  def subsystem_dependencies(cls):
    return super(RemoteSourceTask, cls).subsystem_dependencies() + (RemoteSourceFetcher.Factory.scoped(cls),)

  @classmethod
  def product_types(cls):
    return ['remote_files', 'runtime_classpath']

  @property
  def create_target_dirs(self):
    # Creates the results_dirs but will not cache them.
    return True

  @property
  def cache_target_dirs(self):
    return False

  def stable_root(self, stable_outpath):
    return os.path.join(self.workdir, 'syms', stable_outpath)

  # TODO(mateo): This works well, but the simplicity is totally obscured by all the case-driven logic around
  # determining if the returned paths are files or dirs. Make RemoteSourceFetcher return a (dirs, files) abstraction
  # to clear all that up.
  def execute(self):
    targets = self.context.targets(self._is_remote)
    with self.invalidated(targets, invalidate_dependents=True, topological_order=True) as invalidation_check:
      # The fetches are idempotent operations from the subsystem, invalidation only controls recreating the symlinks.
      for vt in invalidation_check.all_vts:
        remote_source = RemoteSourceFetcher.Factory.scoped_instance(self).create(vt.target)
        fetched = remote_source.path
        safe_mkdir(fetched)

        # Some unfortunate rigamorole to cover for the case where different targets want the same fetched file
        # but extracted/not. Both cases use the same base namespacing so we rely on the target to tell us.
        fetch_dir = fetched if remote_source.extracted else os.path.dirname(fetched)
        filenames = os.listdir(fetched) if remote_source.extracted else [os.path.basename(fetched)]
        stable_outpath = vt.target.namespace + '-{}'.format('extracted') if vt.target.extract else ''

        stable_target_root = self.stable_root(stable_outpath)
        safe_mkdir(stable_target_root)
        if not vt.valid:
          safe_rmtree(stable_target_root)

        for filename in filenames:
          symlink_file = os.path.join(vt.results_dir, filename)
          # This will be false if the symlink or the downloaded blob is missing.
          if not vt.valid or not os.path.isfile(symlink_file):
            safe_rmtree(symlink_file)
            relative_symlink(os.path.join(fetch_dir, filename), symlink_file)

          stable_sym = os.path.join(stable_target_root, filename)
          if not os.path.exists(stable_sym):
            relative_symlink(symlink_file, stable_sym)
          self.context.products.get('remote_files').add(vt.target, stable_target_root).append(filename)
