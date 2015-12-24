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

from pants.build_graph.resources import Resources


class SspTemplate(Resources):
  """Scala Server Pages (ssp) template."""

  def __init__(self, entry_point=None, *args, **kwargs):
    self.entry_point = entry_point
    super(SspTemplate, self).__init__(*args, **kwargs)
