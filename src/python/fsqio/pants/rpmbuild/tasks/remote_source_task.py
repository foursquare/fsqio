# coding=utf-8
# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function, unicode_literals

import os

from pants.backend.jvm.tasks.classpath_products import ClasspathProducts
from pants.task.task import Task
from pants.util.dirutil import relative_symlink, safe_mkdir, safe_rmtree

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
    return super(RemoteSourceTask, cls).subsystem_dependencies() + (RemoteSourceFetcher.scoped(cls),)

  @classmethod
  def product_types(cls):
    return ['remote_files', 'runtime_classpath']

  @classmethod
  def implementation_version(cls):
    return super(RemoteSourceTask, cls).implementation_version() + [('RemoteSourceTask', 3)]

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
    runtime_classpath_product = self.context.products.get_data(
      'runtime_classpath', init_func=ClasspathProducts.init_func(self.get_options().pants_workdir)
    )
    with self.invalidated(targets, invalidate_dependents=True, topological_order=True) as invalidation_check:
      # The fetches are idempotent operations from the subsystem, invalidation only controls recreating the symlinks.
      for vt in invalidation_check.all_vts:
        remote_source = RemoteSourceFetcher(vt.target)
        fetched = remote_source.path
        safe_mkdir(fetched)

        # Some unfortunate rigamorole to cover for the case where different targets want the same fetched file
        # but extracted/not. Both cases use the same base namespacing so we rely on the target to tell us.
        fetch_dir = fetched if remote_source.extracted else os.path.dirname(fetched)
        filenames = os.listdir(fetched) if remote_source.extracted else [os.path.basename(fetched)]
        stable_outpath = vt.target.namespace + '-{}'.format('extracted') if vt.target.extract else ''

        for filename in filenames:
          symlink_file = os.path.join(vt.results_dir, filename)
          if not vt.valid or not os.path.isfile(symlink_file):
            safe_rmtree(symlink_file)
            relative_symlink(os.path.join(fetch_dir, filename), symlink_file)
          self.context.products.get('remote_files').add(vt.target, vt.results_dir).append(filename)

        # The runtime_classpath product is a constructed object that is rooted in the results_dir.
        runtime_classpath_product.add_for_target(vt.target, [('default', vt.results_dir)])
