# coding=utf-8
# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

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


class SpindleThriftLibrary(ExportableJvmLibrary):
  """Defines a target that builds scala_record stubs from a thrift IDL file."""

  def __init__(self, *args, **kwargs):
    super(SpindleThriftLibrary, self).__init__(*args, **kwargs)
    self.add_labels('scala', 'codegen', 'synthetic')

  def _as_jar_dependency(self):
    return ExportableJvmLibrary._as_jar_dependency(self).withSources()
