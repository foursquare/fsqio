# coding=utf-8
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function, unicode_literals

from pants.base.exceptions import TargetDefinitionException
from pants.base.payload import Payload
from pants.base.payload_field import PrimitiveField
from pants.build_graph.target import Target
from six import string_types


class RpmSpecTarget(Target):
  """Define how to build a RPM given its "spec" and related "SOURCES".

  This target defines how to build a RPM package (Red Hat Package Manager) given the "spec" file
  and related sources used by the spec. Invoke the `rpmbuild` task on this target to build all
  RPMs defined in the spec.
  """
  # NOTE(mateo): Targets are generally named after what they will become, so this should become RpmTarget or RpmPackage.
  # Additionally, I believe this plugin should also support codegenning specs from BUILD files, at least
  # for the vanilla case where we unpack a tarball. IMO, writing the spec file is very time consuming and most need only
  # a very common pattern. Needs discussion since it is possibly unworkable for a reason I don't know yet.

  @classmethod
  def alias(cls):
    return 'rpm_spec'

  @property
  def rpm_spec(self):
    return self.payload.rpm_spec.relative_to_buildroot()[0]

  @property
  def remote_sources(self):
    return self.payload.remote_sources

  @property
  def defines(self):
    return self.payload.defines

  @staticmethod
  def _validate_remote_source(remote_source):
    return isinstance(remote_source, string_types) or \
      (
        isinstance(remote_source, tuple) and len(remote_source) == 2 and
        isinstance(remote_source[0], string_types) and
        isinstance(remote_source[1], string_types)
      )

  def __init__(
    self,
    address=None,
    spec=None,
    sources=None,
    remote_sources=None,
    defines=None,
    payload=None,
    **kwargs
  ):
    """
    :param spec: the RPM spec file to use to build the RPMs
    :param spec: string
    :param sources: source files to be placed in the rpmbuild SOURCES directory
    :type sources: ``Fileset`` or list of strings. Paths are relative to the
      BUILD file's directory.
    :param remote_sources: URLs for files to download and place in the rpmbuild SOURCES directory
    :type remote_sources: list[str or tuple(str, str)]
    :param defines: macro definitions to pass into rpmbuild
    :type defines: dict[str, str]
    """

    self.address = address

    # Validate the parameters.
    if spec and not isinstance(spec, string_types):
      raise TargetDefinitionException(self, 'spec must be a single relative file path')
    remote_sources = remote_sources or []
    if not isinstance(remote_sources, list) or any([not self._validate_remote_source(x) for x in remote_sources]):
      raise TargetDefinitionException(self, 'remote_sources must be a list of either a string or tuple of two strings')
    defines = defines or {}
    if not isinstance(defines, dict):
      raise TargetDefinitionException(self, 'defines must be a dictionary')

    payload = payload or Payload()
    payload.add_fields({
      'rpm_spec': self.create_sources_field([spec], address.spec_path, key_arg='rpm_spec'),
      'sources': self.create_sources_field(sources, address.spec_path, key_arg='sources'),
      'remote_sources': PrimitiveField(remote_sources),
      'defines': PrimitiveField(defines),
    })

    # Ensure that only a single spec file was resolved.
    if len(payload.rpm_spec.relative_to_buildroot()) != 1:
      raise TargetDefinitionException(self, 'spec must be a single relative file path')

    super(RpmSpecTarget, self).__init__(address=address, payload=payload, **kwargs)
