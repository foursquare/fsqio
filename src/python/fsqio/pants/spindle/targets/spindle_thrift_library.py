# coding=utf-8
# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function, unicode_literals

from pants.backend.jvm.targets.jvm_target import JvmTarget


class SpindleThriftLibrary(JvmTarget):
  """Defines a target that builds scala_record stubs from a thrift IDL file."""

  def __init__(self, *args, **kwargs):
    super(SpindleThriftLibrary, self).__init__(*args, **kwargs)
