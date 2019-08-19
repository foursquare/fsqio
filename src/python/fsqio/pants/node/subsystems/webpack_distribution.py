# coding=utf-8
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function, unicode_literals

import logging

from pants.subsystem.subsystem import Subsystem


logger = logging.getLogger(__name__)


class WebPackDistribution(Subsystem):
  """Represents a self-bootstrapping Node distribution."""

  options_scope = 'webpack'
  name = 'webpack'

  def get_distribution_args(self):
    return []
