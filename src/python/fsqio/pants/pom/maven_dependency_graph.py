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

from collections import defaultdict
import logging

from fsqio.pants.pom.sort_projects import sort_projects


logger = logging.getLogger(__name__)
logging.getLogger('fsqio.pants.pom').setLevel(logging.WARN)


def defaultdictset():
  return defaultdict(set)

class MavenDependencyGraph(object):
  def __init__(self):
    # unversioned coordinate -> {rev -> set<Dependency>}
    self._coord_to_versioned_deps = defaultdict(defaultdictset)
    # versioned coordinate -> set<versioned coordinate>
    self._coord_to_provided_artifacts = defaultdict(set)
    self._used_pins = set()
    self._used_global_excludes = set()

  def used_global_pin(self, pin):
    self._used_pins.add(pin)

  def used_global_exclude(self, exclude):
    self._used_global_excludes.add(exclude)

  def ensure_node(self, coord):
    if coord not in self._coord_to_versioned_deps:
      self._coord_to_versioned_deps[coord.unversioned] = defaultdict(set)
      if coord.version is not None:
        self._coord_to_versioned_deps[coord.unversioned][coord.version] = set()

  def add_dependency(self, dependee_coord, dependency):
    # Strip some attributes of MavenDependency we don't care about in this context,
    # for the sake of simplicity and consistency.
    dependency = dependency._replace(exclusions=frozenset(), systemPath=None, optional=None)
    self.ensure_node(dependee_coord.unversioned)
    self.ensure_node(dependency.coordinate.unversioned)
    dependee_versioned_deps = self._coord_to_versioned_deps[dependee_coord.unversioned]
    dependee_versioned_deps[dependee_coord.version].add(dependency)

  def add_provided_artifacts(self, coord, fetcher_url, artifact_coords):
    """"Map the coordinate to its provided artifact, indicating the url of the repo where the artifact was found."""
    if artifact_coords:
      artifact_coords = [artifact_coords[0]._replace(repo_url=fetcher_url)]
    self._coord_to_provided_artifacts[coord].update(artifact_coords)

  def artifacts_provided_by_coord(self, coord):
    return list(self._coord_to_provided_artifacts[coord])

  def merge(self, rhs):
    for rhs_unversioned_coord in rhs._coord_to_versioned_deps.keys():
      versioned_deps = self._coord_to_versioned_deps[rhs_unversioned_coord]
      for rev, dep_set in rhs._coord_to_versioned_deps[rhs_unversioned_coord].items():
        versioned_deps[rev] = versioned_deps.get(rev, set()) | dep_set
    for rhs_coord in rhs._coord_to_provided_artifacts.keys():
      self._coord_to_provided_artifacts[rhs_coord] |= rhs._coord_to_provided_artifacts[rhs_coord]
    self._used_pins |= rhs._used_pins
    self._used_global_excludes |= rhs._used_global_excludes
    return self

  def unversioned_dep_graph(self):
    ret = {}
    for coord, rev_to_deps in self._coord_to_versioned_deps.items():
      ret[coord] = set()
      for dep_set in rev_to_deps.values():
        for dep in dep_set:
          ret[coord].add(dep.coordinate.unversioned)
    return ret

  def reverse_unversioned_dep_graph(self):
    ret = defaultdict(set)
    for coord, rev_to_deps in self._coord_to_versioned_deps.items():
      ret[coord] = ret.get(coord, set())
      for dep_set in rev_to_deps.values():
        for dep in dep_set:
          ret[dep.coordinate.unversioned].add(coord)
    return ret

  def conflicted_dependencies(self):
    topo_sorted_deps = sort_projects(self.unversioned_dep_graph())
    def conflicted_deps_iter():
      for coord in topo_sorted_deps:
        rev_to_deps = self._coord_to_versioned_deps[coord]
        if len(rev_to_deps) > 1:
          yield (coord, rev_to_deps.keys())
    return list(conflicted_deps_iter())

  def artifact_closure(self):
    def artifact_closure_iter():
      for coord, rev_to_deps in self._coord_to_versioned_deps.items():
        if len(rev_to_deps) > 1:
          logger.warn('Returning an artifact closure with multiple revs for {}'.format(coord))
        for rev in rev_to_deps.keys():
          yield coord._replace(version=rev)

    return list(artifact_closure_iter())

  def __getitem__(self, key):
    return self._coord_to_versioned_deps[key]

  def __str__(self):
    lines = []
    lines.append('MavenDependencyGraph(')
    for coord in sorted(self._coord_to_versioned_deps.keys()):
      rev_to_deps = self._coord_to_versioned_deps[coord]
      lines.append('\t{}'.format(coord))
      for rev in sorted(rev_to_deps.keys()):
        lines.append('\t\t{} ->'.format(rev))
        for dep in sorted(rev_to_deps[rev]):
          lines.append('\t\t\t* {}'.format(dep.coordinate))
      lines.append('')
    lines.append(')\n')
    return '\n'.join(lines)
