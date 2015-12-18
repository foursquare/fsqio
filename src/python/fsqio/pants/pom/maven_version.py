# coding=utf-8
# Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

from __future__ import (
  absolute_import,
  division,
  generators,
  nested_scopes,
  print_function,
  unicode_literals,
  with_statement,
)

from functools import total_ordering
import logging
import re


logger = logging.getLogger(__name__)


@total_ordering
class MavenVersion(object):
  MAVEN_VERSION_REGEX = re.compile(
    r'(?P<major>\d+)\.'
    r'(?P<minor>\d+)'
    r'(?P<rev>\.\d+)?'
    r'(?P<qualifier_or_build>-\w+)?'
  )

  def __init__(self, version_str):
    self._version_str = version_str
    match = self.MAVEN_VERSION_REGEX.match(self._version_str)
    if not match:
      raise Exception('Invalid Maven version string: {}'.format(self._version_str))
    self.major = int(match.group('major'))
    self.minor = int(match.group('minor'))
    rev = match.group('rev')
    if rev:
      self.rev = int(rev[1:])
    else:
      self.rev = 0
    qualifier_or_build = match.group('qualifier_or_build')
    if qualifier_or_build:
      qualifier_or_build = qualifier_or_build[1:]
      if re.match(r'\d+', qualifier_or_build):
        self.build = int(qualifier_or_build)
        self.qualifier = None
      else:
        self.build = None
        self.qualifier = qualifier_or_build
    else:
      self.build = 0
      self.qualifier = None

  def __lt__(self, rhs):
    if (self.major, self.minor, self.rev) != (rhs.major, rhs.minor, rhs.rev):
      return (self.major, self.minor, self.rev) < (rhs.major, rhs.minor, rhs.rev)
    elif self.qualifier is None and rhs.qualifier is not None:
      return True
    elif rhs.qualifier is None and self.qualifier is not None:
      return False
    elif self.qualifier is not None and rhs.qualifier is not None:
      return self.qualifier.lower() < rhs.qualifier.lower()
    else:
      return self.build < rhs.build

  def __eq__(self, rhs):
    self_tuple = (self.major, self.minor, self.rev, self.qualifier, self.build)
    rhs_tuple = (rhs.major, rhs.minor, rhs.rev, rhs.qualifier, rhs.build)
    return self_tuple == rhs_tuple

  def __repr__(self):
    return 'MavenVersion{}'.format((self.major, self.minor, self.rev, self.qualifier, self.build))

  def __str__(self):
    return self._version_str


class MavenVersionRangeRef(object):
  """A container and parser for Maven Version Range specs.

  See: http://docs.oracle.com/middleware/1212/core/MAVEN/maven_version.htm#MAVEN402
  or http://docs.codehaus.org/display/MAVEN/Dependency+Mediation+and+Conflict+Resolution#DependencyMediationandConflictResolution-DependencyVersionRanges
  or any of the other variously undated or outdated or ignored documentations of this spec.
  """

  # e.g. '1.0'.  "Suggested" because someone thought it was a good idea to make '1.0' the
  # "eh, whatever" spec and '[1.0]' the "no, seriously" spec.
  SUGGESTED_VERSION_REGEX = re.compile(r'^([^\[\](),<>=]+)$')

  # e.g. '[1.0]'.  See above.
  EXACT_RANGE_SPEC_REGEX = re.compile(r'^\[{}\]$'.format(SUGGESTED_VERSION_REGEX.pattern))

  # Just for code cleanliness.  Matches '[foo]', '(foo)', '[foo)', '(foo]'.
  RANGE_REF_PATTERN = r'[\[(].*?[\]|)]'

  # A comma delimited list of the above pattern, with some generous whitespace guards.
  RANGES_REGEX = re.compile(
    r'^\s*{range_ref_pattern}'
    r'(?:\s*,{range_ref_pattern}\s*)*$'
    .format(range_ref_pattern=RANGE_REF_PATTERN)
  )

  # The same as `RANGE_REF_PATTERN`, but pattern matched into component parts.
  RANGE_REF_REGEX = re.compile(
    r'(?P<begin_range>\[|\()'
    r'(?P<range_content>.*?)'
    r'(?P<end_range>\]|\))'
  )

  # Very liberally whitespace guarded pattern match of "range_content" in the above pattern.
  # Matches ',foo', 'foo,', 'foo,bar', etc.
  RANGE_CONTENT_REGEX = re.compile(
    r'^\s*'
    r'(?P<left_version>.*?)'
    r'\s*,\s*'
    r'(?P<right_version>.*?)'
    r'\s*$'
  )

  def __init__(self, ref_str):
    self._ref_str = ref_str.strip()
    self._parse_ref_to_matchers()

  def _parse_ref_to_matchers(self):
    # Special case: This is just a version with no extra bells and whistles.
    # Also the most common case.
    match = self.SUGGESTED_VERSION_REGEX.match(self._ref_str)
    if match:
      self._matchers = [lambda candidate: candidate == match.groups(0)]
      return

    # The "exact, no really" spec.  We handle this and the loose "this one, I guess" spec
    # identically.
    match = self.EXACT_RANGE_SPEC_REGEX.match(self._ref_str)
    if match:
      self._matchers = [lambda candidate: candidate == match.groups(0)]
      return

    # A sequence of range specs
    matches = self.RANGES_REGEX.findall(self._ref_str)
    if not matches:
      raise Exception('Invalid Maven Version Range ref: {0}'.format(self._ref_str))

    self._matchers = []
    for matched_substr in matches:
      range_match = self.RANGE_REF_REGEX.match(matched_substr)
      begin_range_token = range_match.group('begin_range')
      end_range_token = range_match.group('end_range')
      range_content_match = self.RANGE_CONTENT_REGEX.match(range_match.group('range_content'))

      left_version = range_content_match.group('left_version')
      left_matcher = None
      if left_version:
        left_maven_version = MavenVersion(left_version)
        if begin_range_token == '[':
          left_matcher = lambda candidate: left_maven_version <= candidate
        else:
          left_matcher = lambda candidate: left_maven_version < candidate
      else:
        left_matcher = lambda _: True

      right_version = range_content_match.group('right_version')
      right_matcher = None
      if right_version:
        right_maven_version = MavenVersion(right_version)
        if begin_range_token == ']':
          right_matcher = lambda candidate: candidate <= right_maven_version
        else:
          right_matcher = lambda candidate: candidate < right_maven_version
      else:
        right_matcher = lambda _: True
      self._matchers.append(lambda mv: left_matcher(mv) and right_matcher(mv))

  def matches(self, maven_version):
    for matcher in self._matchers:
      if matcher(maven_version):
        return True
    return False
