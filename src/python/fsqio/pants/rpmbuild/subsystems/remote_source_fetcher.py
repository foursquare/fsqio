# coding=utf-8
# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function, unicode_literals

import os
import os

from pants.binaries.binary_tool import BinaryToolBase
from pants.fs.archive import archiver_for_path
from pants.util.contextutil import temporary_dir
from pants.util.memo import memoized_method, memoized_property


class RemoteSourceFetcher(BinaryToolBase):
  """Fetcher for remote sources which uses BinaryToolBase pipeline."""
  # This allows long-lived caching of remote downloads, which are painful to to over and over when they aren't changing.

  # NOTE(mateo): Upstream Pants saw this subsystem and ended up adding a subset of the features.
  # That subset needs to be audited and consumed, with a longer-term goal of patching upstream to add
  # any of the following features we cannot live without.
  #
  # RemoteSources plugin provides the following features (uniquely or otherwise):
  #  * Long-lived caching of downloaded files
  #     - Only invalidated by version changes - otherwise considered cached
  #     - Kept outsidce .pants.d or artifact cache, alongside Pants downloaded tooling.
  #     - Atomic downloads so we aren't poisoned by corrupted downloads.
  #  * Addressable in BUILD files
  #     - These are considered "versioned" and can be referenced as dependencies.
  #     - RpmBuilder as canonical consumer - caching bootstrapped source bundles.
  #  * Fetched on demand, either directly or transitively
  #     - If you call `./pants rpmbuild src/redhat/libevent` only then should it bootstrap the source bundle.
  #  * Unpack attribute in the target
  #     - Extract as an addressable feature.
  #
  # These features mean that we can add new bootstrapped downloads strictly by editing BUILD files and they
  # will be fetched on demand, and cached ~forever. This is incredibly powerful for engineers without direct
  # Pants development experience.

  # TODO(mateo): Either fully adapt the remote_sources plugin for the new BinaryToolBase interface or
  # work with upstream until UnpackJars is robust enough for our use cases.

  # The upstream interface uses this for the "name" because it expects a new Subsystem for every boostrapped
  # tool. We set the name in the BUILD file, which is interpolated through overrides below.
  options_scope = 'remote-fetcher'

  def __init__(self, remote_target):
    self.name = remote_target.namespace
    self._filename = remote_target.filename
    self._extract = remote_target.extract or False
    self._version = remote_target.version
    self.platform_dependent = remote_target.platform_dependent == "True"

  def get_support_dir(self):
    return 'bin/{}'.format(self.name)

  def version(self, context=None):
    """Returns the version of the specified binary tool."""
    return self._version

  @property
  def _relpath(self):
    return os.path.join(self.get_support_dir(), self.name())

  @property
  def extracted(self):
    return self._extract

  def _construct_path(self, context=None):
    fetched = self.select(context)
    if not self._extract:
      return fetched
    unpacked_dir = os.path.dirname(fetched)
    outdir = os.path.join(unpacked_dir, 'unpacked')
    if not os.path.exists(outdir):
      with temporary_dir(root_dir=unpacked_dir) as tmp_root:
        # This is an upstream lever that pattern matches the filepath to an archive type.
        archiver = archiver_for_path(fetched)
        archiver.extract(fetched, tmp_root)
        os.rename(tmp_root, outdir)
    return os.path.join(outdir)

  @memoized_method
  def _select_for_version(self, version):
    # Override, since we include the extension in the actual filename
    # (a compromise so we could support downloading files with no extension).
    return self._binary_util.select(
      supportdir=self.get_support_dir(),
      version=version,
      name='{}'.format(self._filename),
      platform_dependent=self.platform_dependent,
      archive_type=self.archive_type)

  @memoized_property
  def path(self, context=None):
    """Fetch the binary and return the full file path.

    Safe to call repeatedly, the fetch itself is idempotent.
    """
    return self._construct_path()
