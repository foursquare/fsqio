# coding=utf-8
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function, unicode_literals

import os

from pants.base.exceptions import TargetDefinitionException
from pants.contrib.node.targets.node_module import NodeModule
from pants.util.memo import memoized_property


class WebPackModule(NodeModule):

  @memoized_property
  def npm_json(self):
    npm_json = os.path.join(self.address.spec_path, 'package-lock.json')
    if npm_json not in self.sources_relative_to_buildroot():
      raise TargetDefinitionException(
        self,
        "WebPackModules are required to have a 'package-lock.json' file in sources.",
      )
    return npm_json


class NpmResource(WebPackModule):
  pass
