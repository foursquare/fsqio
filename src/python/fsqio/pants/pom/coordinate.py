# coding=utf-8
# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import

from collections import namedtuple


coordinate_attrs = [
  'groupId',
  'artifactId',
  'version',
  'packaging',
  'classifier',
  'repo_url',
]

class Coordinate(namedtuple('Coordinate', coordinate_attrs)):
  """A wrapper, parser, and constructor classmethods for the Maven POM coordinate.

  See https://maven.apache.org/pom.html#Maven_Coordinates
  """

  @classmethod
  def from_string(cls, coord_str):
    parts = coord_str.split(':')
    if len(parts) == 3:
      return cls(
        groupId=parts[0],
        artifactId=parts[1],
        version=parts[2],
        packaging='jar',
        classifier=None,
      )
    elif len(parts) == 4:
      return cls(
        groupId=parts[0],
        artifactId=parts[1],
        version=parts[3],
        packaging=parts[2],
        classifier=None,
      )
    elif len(parts) == 5:
      return cls(
        groupId=parts[0],
        artifactId=parts[1],
        version=parts[4],
        packaging=parts[2],
        classifier=parts[3],
      )
    else:
      raise ValueError('Malformed Maven Coordinate string: {}'.format(coord_str))

  @property
  def unversioned(self):
    return self._replace(version=None)

  @property
  def artifact_path(self):
    if self.classifier:
      return '/'.join([
        '{group}',
        '{artifactId}',
        '{version}',
        '{artifactId}-{version}-{classifier}.{packaging}',
      ]).format(
        group=self.groupId.replace('.', '/'),
        artifactId=self.artifactId,
        version=self.version,
        classifier=self.classifier,
        packaging=self.packaging,
      )
    else:
      return '/'.join([
        '{group}',
        '{artifactId}',
        '{version}',
        '{artifactId}-{version}.{packaging}',
      ]).format(
        group=self.groupId.replace('.', '/'),
        artifactId=self.artifactId,
        version=self.version,
        packaging=self.packaging,
      )

  def __str__(self):
    return '{}:{}:{}:{}:{}'.format(
      self.groupId,
      self.artifactId,
      self.version,
      self.packaging,
      self.classifier,
    )
