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


logger = logging.getLogger('fsqio.pants.pom')


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
  MAVEN_METADATA_XML_URL = '/'.join([
    '{repo}',
    '{group}',
    '{artifact}',
    'maven-metadata.xml',
  ])

  def __init__(self, name, repo):
    self._cache = {}
    self.name = name
    self.repo = repo

  def get_pom(self, groupId, artifactId, version):
    coordinate = Coordinate(groupId, artifactId, version, 'pom', None, self.repo)
    if coordinate not in self._cache:
      pom_url = self.BASIC_ARTIFACT_PATH.format(
        repo=self.repo,
        group=groupId.replace('.', '/'),
        artifact=artifactId,
        version=version,
        ext='pom',
      )
      response = requests.get(pom_url)
      logger.debug('Request for {} from {} returned: {}.'.format(coordinate, self.repo, response.status_code))
      self._cache[coordinate] = response
    response = self._cache[coordinate]
    return response

  def get_maven_metadata_xml(self, groupId, artifactId):
    coordinate = Coordinate(groupId, artifactId, None, 'xml', None)
    if coordinate not in self._cache:
      metadata_url = self.MAVEN_METADATA_XML_URL.format(
        repo=self.repo,
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
          repo=self.repo,
          group=coordinate.groupId.replace('.', '/'),
          artifact=coordinate.artifactId,
          version=coordinate.version,
          classifier=coordinate.classifier,
          ext=coordinate.packaging,
        )
      else:
        resource_url = self.BASIC_ARTIFACT_PATH.format(
          repo=self.repo,
          group=coordinate.groupId.replace('.', '/'),
          artifact=coordinate.artifactId,
          version=coordinate.version,
          ext=coordinate.packaging,
        )
      response = requests.head(resource_url)
      self._cache[coordinate] = response
    # Allow 200 or 302 (see https://github.com/kennethreitz/requests/blob/master/requests/status_codes.py)
    valid_codes = (requests.codes.ok, requests.codes.found) # pylint: disable=no-member
    return self._cache[coordinate].status_code in valid_codes


class ChainedFetcher(ArtifactFetcher):

  _cache = {}

  def __init__(self, fetchers):
    self._fetchers = []
    for fetcher in fetchers:
      for name, repo_url in fetcher.items():
        if name not in self._cache:
          self._cache[name] = ArtifactFetcher(name, repo_url)
        self._fetchers.append(self._cache[name])

  def __iter__(self):
    i = 0
    while i < len(self._fetchers):
      yield self._fetchers[i]
      i += 1

  def resolve_pom(self, groupId, artifactId, version, jar_coordinate=None):
    """Descend the list of fetchers until a pom or resource is found.

    If sucessful, returns the fetcher and pom_contents. If either of those two are missing,
    return None in its place.

    :returns: tuple(ArtifactFetcher, string)
    """

    coordinate = Coordinate(groupId, artifactId, version, 'pom', None, None)
    for fetcher in self._fetchers:
      logger.debug('Attempting to get {} pom from fetcher: {}.\n'.format(coordinate, fetcher.name))
      response = fetcher.get_pom(groupId, artifactId, version)
      try:
        logger.debug('{}: returned {} for {}.'.format(fetcher.name, response.status_code, coordinate))
        response.raise_for_status()
        return fetcher, response.content
      except requests.exceptions.HTTPError as e:
        if jar_coordinate:
          if fetcher.resource_exists(jar_coordinate):
            logger.debug(
              '{}: Found a {} but no pom - treating as intransitive dep: {}.\n'
              .format(fetcher.name, jar_coordinate, coordinate)
            )
            return fetcher, None

    return None, None
