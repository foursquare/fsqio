# coding=utf-8
# Copyright 2019 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function, unicode_literals

import urllib

from pants.subsystem.subsystem import Subsystem
from pants.util.memo import memoized_property


class ConfluenceSubsystem(Subsystem):
  options_scope = 'confluence-wiki'

  @staticmethod
  def confluence_url_builder(page):
    config = page.provides[0].config
    title = config['title']
    full_url = '{}/wiki/spaces/{}/{}'.format(
      ConfluenceSubsystem.wiki_url,
      config['space'],
      urllib.quote_plus(title),
    )
    return title, full_url

  @classmethod
  def register_options(cls, register):
    super(ConfluenceSubsystem, cls).register_options(register)
    # TODO(mateo): This only supports a single wiki url, should a map of wiki_name:url.
    # This is not trivial to unwind, the base plugin assumed self-hosted wiki and url builders.
    register(
      '--wiki-url',
      default=None,
      advanced=True,
      help='Wiki hostname.',
    )
    register(
      '--email-domain',
      advanced=True,
      help='Options default domain. For human@foo.com, use @foo.com. Note: Overrides the email-domain option.',
    )

  @memoized_property
  def wiki_url(self):
    wiki_url = self.get_options().wiki_url
    if wiki_url is None:
      raise ValueError("No wiki URL set! Please set option --{}-wiki-url.".format(self.options_scope))
    return wiki_url

  @memoized_property
  def email_domain(self):
    email_domain = self.get_options().email_domain
    if email_domain is None:
      raise ValueError("No email domain is set! Please set option --{}-email-domain.".format(self.options_scope))
    return email_domain
