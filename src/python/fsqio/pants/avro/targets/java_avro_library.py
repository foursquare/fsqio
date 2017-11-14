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

from pants.backend.jvm.targets.exportable_jvm_library import ExportableJvmLibrary
from pants.base.payload import Payload


class JavaAvroLibrary(ExportableJvmLibrary):
  """Defines a target that builds Java code from Avro schema, protocol, or IDL files."""

  def __init__(self, payload=None, **kwargs):
    payload = payload or Payload()
    super(JavaAvroLibrary, self).__init__(payload=payload, **kwargs)

  @classmethod
  def alias(cls):
    return 'java_avro_library'
