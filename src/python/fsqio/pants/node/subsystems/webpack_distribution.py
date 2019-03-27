# coding=utf-8
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function, unicode_literals

import logging
import logging

from pants.option.custom_types import file_option
from pants.subsystem.subsystem import Subsystem
from pants.util.memo import memoized_method


logger = logging.getLogger(__name__)


class WebPackDistribution(Subsystem):
  """Represents a self-bootstrapping Node distribution."""

  options_scope = 'webpack'
  name = 'webpack'

  @classmethod
  def register_options(cls, register):
    register(
      '--user-config',
      advanced=True,
      type=file_option,
      fingerprint=True,
      help='Path to npmrc userconfig file. If unset, falls to npm default.',
    )
    super(WebPackDistribution, cls).register_options(register)

  @memoized_method
  def get_distribution_args(self):
    user_config = self.get_options().user_config
    return ['--user-config={}'.format(user_config)] if user_config else []
