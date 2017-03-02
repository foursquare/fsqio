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

from pants.base.payload import Payload
from pants.base.payload_field import PrimitiveField
from pants.build_graph.target import Target


class RemoteSource(Target):
  """Represent a versioned bundle or file that can be used as source input during RPM builds."""

  @classmethod
  def alias(cls):
    return 'remote_source'

  def __init__(
    self,
    version=None,
    platform=None,
    arch=None,
    namespace=None,
    payload=None,
    **kwargs):
    """
    Represent a remote source to be fetched as part of the RpmBuild process.

    :param string name: Basename of the source package or file, as well as the target name.
      e.g. 'node.tar.gz' or 'thrift'.
    :param string version: version of the source distribution.
    :param string platform: Intended platform. Currently defaults to linux
    :param string arch: Intended architecture of the package. Currently defaults to 'x86_64'.
    :param string namespace: Directory name that holds these sources. Defaults to using the split filename,
      e.g. 'node' for 'node.tar.gz' or 'thrift' for 'thrift'. This argument is mostly for tricky edge cases.
    """

    # TODO(mateo): Support platform-independent bundles, which is what most source distributions will be.
    self.version = version
    self.platform = platform or 'linux'
    self.arch = arch or 'x86_64'
    self.namespace = namespace or kwargs['name'].split('.')[0]
    payload = payload or Payload()
    payload.add_fields({
      'version': PrimitiveField(self.version),
      'platform': PrimitiveField(self.platform),
      'arch': PrimitiveField(self.arch),
      'namespace': PrimitiveField(self.namespace),
    })
    super(RemoteSource, self).__init__(payload=payload, **kwargs)
