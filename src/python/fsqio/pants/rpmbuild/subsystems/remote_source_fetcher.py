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

from pants.binaries.binary_util import BinaryUtil
from pants.subsystem.subsystem import Subsystem
from pants.util.memo import memoized_property


class RemoteSourceUtil(BinaryUtil):
  """Encapsulates access to hosted remote sources."""

  class RemoteSourceNotFound(BinaryUtil.BinaryNotFound):
    """No file or bundle found at any registered baseurl."""

  class Factory(Subsystem):

    options_scope = 'binaries'

    @classmethod
    def create(cls):
      options = cls.global_instance().get_options()
      return RemoteSourceUtil(
        options.baseurls, options.fetch_timeout_secs, options.pants_bootstrapdir, options.path_by_id
      )

  @staticmethod
  def uname_func():
    # Force pulling down linux paths, since this is destined to be built in a Docker container.
    return "linux", "foo", "bar", "baz", "x86_64"

  def select_binary(self, supportdir, version, name):
    # Enforces using the linux Docker environment since this is for rpmbuilder.
    # TODO(mateo): Derive the uname_func from the platfrom in the RpmBuilder.
    binary_path = self._select_binary_base_path(supportdir, version, name, uname_func=self.uname_func)
    return self._fetch_binary(name=name, binary_path=binary_path)

class RemoteSourceFetcher(object):
  """Fetcher for remote sources which uses BinaryUtil pipeline."""
  # This allows long-lived caching of remote downloads, which are painful to to over and over when they aren't changing.

  class Factory(Subsystem):
    options_scope = 'remote-fetcher'

    @classmethod
    def subsystem_dependencies(cls):
      return super(RemoteSourceFetcher.Factory, cls).subsystem_dependencies() + (BinaryUtil.Factory,)

    @classmethod
    def register_options(cls, register):
      register('--supportdir', advanced=True, default='bin', help='Find sources under this dir.'
        'Used as part of the path to lookup the tool with --binary-util-baseurls and --pants-bootstrapdir'
      )

    def create(self, remote_target):
      remote_source_util = RemoteSourceUtil.Factory.create()
      options = self.get_options()
      relpath = os.path.join(options.supportdir, remote_target.namespace)
      return RemoteSourceFetcher(remote_source_util, relpath, remote_target.version, remote_target.name)

  def __init__(self, remote_source_util, relpath, version, filename):
    self.remote_source_util = remote_source_util
    self._relpath = relpath
    self._version = version
    self._filename = filename

  @property
  def version(self):
    return self._version

  @memoized_property
  def path(self):
    """Fetch the binary and return the full file path.

    Safe to call repeatedly, the fetch itself is idempotent.
    """
    return self.remote_source_util.select_binary(self._relpath, self.version, self._filename)
