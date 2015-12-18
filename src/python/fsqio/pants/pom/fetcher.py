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

import logging

import requests

from fsqio.pants.pom.coordinate import Coordinate


logger = logging.getLogger(__name__)


NEXUS_PUBLIC = 'http://nexus.prod.foursquare.com/nexus/content/groups/public'


class ArtifactFetcher(object):
  BASIC_ARTIFACT_PATH = '/'.join([
    '{repo}',
    '{group}',
    '{artifact}',
    '{version}',
    '{artifact}-{version}.{ext}',
  ])
  CLASSIFIER_ARTIFACT_PATH = '/'.join([
    '{repo}',
    '{group}',
    '{artifact}',
    '{version}',
    '{artifact}-{version}-{classifier}.{ext}',
  ])
  NEXUS_MAVEN_METADATA_XML_URL = '/'.join([
    '{repo}',
    '{group}',
    '{artifact}',
    'maven-metadata.xml',
  ])

  def __init__(self):
    self._cache = {}

  _instance = None
  @classmethod
  def instance(cls):
    if cls._instance is None:
      cls._instance = cls()
    return cls._instance

  def get_pom(self, groupId, artifactId, version):
    coordinate = Coordinate(groupId, artifactId, version, 'pom', None)
    if coordinate not in self._cache:
      pom_url = self.BASIC_ARTIFACT_PATH.format(
        repo=NEXUS_PUBLIC,
        group=groupId.replace('.', '/'),
        artifact=artifactId,
        version=version,
        ext='pom',
      )
      response = requests.get(pom_url)
      self._cache[coordinate] = response
    response = self._cache[coordinate]
    response.raise_for_status()
    return response.content

  def get_maven_metadata_xml(self, groupId, artifactId):
    coordinate = Coordinate(groupId, artifactId, None, 'xml', None)
    if coordinate not in self._cache:
      metadata_url = self.NEXUS_MAVEN_METADATA_XML_URL.format(
        repo=NEXUS_PUBLIC,
        group=groupId.replace('.', '/'),
        artifact=artifactId,
      )
      response = requests.get(metadata_url)
      self._cache[coordinate] = response
    response = self._cache[coordinate]
    response.raise_for_status()
    return response.content

  def resource_exists(self, coordinate):
    if coordinate not in self._cache:
      if coordinate.classifier:
        resource_url = self.CLASSIFIER_ARTIFACT_PATH.format(
          repo=NEXUS_PUBLIC,
          group=coordinate.groupId.replace('.', '/'),
          artifact=coordinate.artifactId,
          version=coordinate.version,
          classifier=coordinate.classifier,
          ext=coordinate.packaging,
        )
      else:
        resource_url = self.BASIC_ARTIFACT_PATH.format(
          repo=NEXUS_PUBLIC,
          group=coordinate.groupId.replace('.', '/'),
          artifact=coordinate.artifactId,
          version=coordinate.version,
          ext=coordinate.packaging,
        )
      response = requests.head(resource_url)
      self._cache[coordinate] = response
    return self._cache[coordinate].status_code == 200
