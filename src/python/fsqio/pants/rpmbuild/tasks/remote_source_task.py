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
from pants.util.dirutil import relative_symlink

from fsqio.pants.rpmbuild.subsystems.remote_source_fetcher import RemoteSourceFetcher
from fsqio.pants.rpmbuild.targets.remote_source import RemoteSource


class RemoteSourceTask(Task):
  """Fetch versioned files from a URL."""

  @classmethod
  def subsystem_dependencies(cls):
    return super(RemoteSourceTask, cls).subsystem_dependencies() + (RemoteSourceFetcher.Factory.scoped(cls),)

  @classmethod
  def product_types(cls):
    return ['remote_files']

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
        source_path = remote_source.path
        file_dir = source_path if remote_source.extracted else os.path.dirname(source_path)
        file_namez = os.listdir(source_path) if remote_source.extracted else [os.path.basename(source_path)]
        if not vt.valid:
          for filename in file_namez:
            relative_symlink(filename, os.path.join(vt.results_dir, filename))
        self.context.products.get('remote_files').add(vt.target, vt.results_dir).extend(file_namez)
