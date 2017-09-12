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
from pants.util.dirutil import relative_symlink, safe_delete, safe_mkdir

from fsqio.pants.rpmbuild.subsystems.remote_source_fetcher import RemoteSourceFetcher
from fsqio.pants.rpmbuild.targets.remote_source import RemoteSource


class RemoteSourceTask(Task):
  """Fetch versioned files from a URL."""

  @classmethod
  def subsystem_dependencies(cls):
    return super(RemoteSourceTask, cls).subsystem_dependencies() + (RemoteSourceFetcher.Factory.scoped(cls),)

  @classmethod
  def product_types(cls):
    return ['remote_files', 'runtime_classpath']

  # Create results_dirs but do not cache them. This allows a stable file path for adding to classpath but each user
  # must bootstrap individually.
  @property
  def create_target_dirs(self):
    return True

  @property
  def cache_target_dirs(self):
    return False

  @staticmethod
  def _is_remote(target):
    return isinstance(target, RemoteSource)

  def execute(self):
    targets = self.context.targets(self._is_remote)
    with self.invalidated(targets, invalidate_dependents=True, topological_order=True) as invalidation_check:
      # The fetches are idempotent operations from the subsystem, invalidation only controls recreating the symlinks.
      for vt in invalidation_check.all_vts:
        remote_source = RemoteSourceFetcher.Factory.scoped_instance(self).create(vt.target)
        safe_mkdir(remote_source.path)
        file_dir = remote_source.path if remote_source.extracted else os.path.dirname(remote_source.path)
        file_namez = os.listdir(remote_source.path) if remote_source.extracted else [os.path.basename(remote_source.path)]
        for filename in file_namez:
          symlink_file = os.path.join(vt.results_dir, filename)
          # This will be false if the symlink or the downloaded blob is missing.
          if not vt.valid or not os.path.isfile(os.path.realpath(symlink_file)):
            safe_delete(symlink_file)
            relative_symlink(os.path.join(file_dir, filename), symlink_file)
        self.context.products.get('remote_files').add(vt.target, vt.results_dir).extend(file_namez)
