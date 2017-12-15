# coding=utf-8
# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import

from collections import namedtuple
from copy import deepcopy

from fsqio.pants.pom_resolve.coordinate import Coordinate


MANAGED_DEP_ATTRS = [
  # Note that version is actually a version range ref, but here I maintain name parity
  # with the POM XML.
  'version',
  'classifier',
  'type',
  'scope',
  'systemPath',
  'optional',
  'intransitive',
]

dependency_attrs = ['groupId', 'artifactId', 'exclusions'] + MANAGED_DEP_ATTRS


class Dependency(namedtuple('Dependency', dependency_attrs)):
  """A wrapper and constructor for Maven Dependency nodes.

  See https://maven.apache.org/pom.html#Dependencies
  """

  @classmethod
  def attr_dict_from_tree(cls, tree):
    return {
      attr_key: tree.findtext(attr_key)
      for attr_key in MANAGED_DEP_ATTRS
      if tree.findtext(attr_key) is not None
    }

  @classmethod
  def from_xml(cls, tree, dependency_management):
    groupId = tree.findtext('groupId')
    artifactId = tree.findtext('artifactId')
    type_ = tree.findtext('type') or 'jar'

    attr_dict = deepcopy(dependency_management.get((groupId, artifactId, type_), {}))
    attr_dict.update(cls.attr_dict_from_tree(tree))

    classifier = attr_dict.get('classifier')

    version = attr_dict.get('version')
    if version is None:
      raise ValueError(
        'While parsing XML for dependency ({}, {}, {}), version was not specified and did not'
        ' occur in the passed dependency management section.'
        .format(groupId, artifactId, classifier))

    scope = attr_dict.get('scope', 'compile')
    if "," in scope:
      scopes = scope.split(",")
      # HACK(iant): Use a hierarchy and just pick one
      scope = (s for s in ('compile', 'provided', 'runtime', 'test', 'system') if s in scopes).next()
    if scope not in ('compile', 'provided', 'runtime', 'test', 'system'):
      raise ValueError(
        'While parsing XML for dependency ({}, {}), invalid scope: {}'
        .format(groupId, artifactId, scope))

    systemPath = attr_dict.get('systemPath')
    if systemPath and scope != 'system':
      raise ValueError(
        'While parsing XML for dependency ({groupId}, {artifactId}),'
        ' systemPath set when scope is not "system".'
        ' scope: {scope}.  systemPath: {systemPath}.'
        .format(
          groupId=groupId,
          artifactId=artifactId,
          scope=scope,
          systemPath=systemPath))

    optional = attr_dict.get('optional')
    if optional and optional.lower() not in ('true', 'false'):
      raise ValueError(
        'While parsing XML for dependency ({groupId}, {artifactId}),'
        ' optional set to a non "true"/"false" value: {optional}.'
        .format(groupId=groupId, artifactId=artifactId, option=optional))
    optional = bool(optional and optional.lower() == 'true')

    exclusions = list(attr_dict.get('exclusions', []))
    for exclusion in tree.findall('exclusions/exclusion'):
        excluded_groupId = exclusion.findtext('groupId')
        excluded_artifactId = exclusion.findtext('artifactId')
        exclusions.append((excluded_groupId, excluded_artifactId))
    exclusions = frozenset(exclusions)

    # NOTE(mateo): This tracks instrantive deps as set in a pom.xml. Dependency instances are created for every
    # JarDependency in 3rdparty, and intransitive is set to True based on the BUILD definition.
    intransitive = bool(('*', '*') in exclusions or scope == 'provided' or optional)

    return cls(
      groupId=groupId,
      artifactId=artifactId,
      version=version,
      classifier=classifier,
      type=type_,
      scope=scope,
      systemPath=systemPath,
      optional=optional,
      exclusions=exclusions,
      intransitive=intransitive,
    )

  @property
  def coordinate(self):
    return Coordinate(
      groupId=self.groupId,
      artifactId=self.artifactId,
      version=self.version,
      packaging=self.type,
      classifier=self.classifier,
      repo_url=None,
    )

  @property
  def pom_coordinate(self):
    return (self.groupId, self.artifactId, self.version)

  @property
  def unversioned_coordinate(self):
    return (self.groupId, self.artifactId)

  def __str__(self):
    return 'Dependency({}, {}, {})'.format(self.groupId, self.artifactId, self.version)
