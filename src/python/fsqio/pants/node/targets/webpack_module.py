# coding=utf-8
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

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

from pants.base.exceptions import TargetDefinitionException
from pants.contrib.node.targets.node_module import NodeModule
from pants.util.memo import memoized_property


class WebPackModule(NodeModule):
  # TODO(mateo): Target depends on having webpack in the json that lists npm dependencies. Enforce that dep somewhere.

  @memoized_property
  def npm_json(self):
    npm_json = os.path.join(self.address.spec_path, 'npm-shrinkwrap.json')
    if npm_json not in self.sources_relative_to_buildroot():
      raise TargetDefinitionException(
        self,
        "WebPackModules are required to have a 'npm-shrinkwrap.json' file in sources.",
      )
    return npm_json
