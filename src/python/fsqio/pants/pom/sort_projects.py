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

from twitter.common.collections import OrderedSet


logger = logging.getLogger(__name__)

class CycleException(Exception):
  """Thrown when a circular dependency is detected."""
  def __init__(self, cycle):
    Exception.__init__(self, 'Cycle detected:\n\t%s' % (
        ' ->\n\t'.join(str(t) for t in cycle)
    ))


def sort_projects(project_dict):
  """:return: the targets that targets depend on sorted from most dependent to least."""
  roots = OrderedSet()
  inverted_deps = defaultdict(OrderedSet)  # target -> dependent targets
  visited = set()
  path = OrderedSet()

  def invert(target):
    if target in path:
      path_list = list(path)
      cycle_head = path_list.index(target)
      cycle = path_list[cycle_head:] + [target]
      raise CycleException(cycle)
    path.add(target)
    if target not in visited:
      visited.add(target)
      for dependency in project_dict[target]:
        inverted_deps[dependency].add(target)
        invert(dependency)
      else:
        roots.add(target)
    path.remove(target)

  for target in project_dict.keys():
    invert(target)

  ordered = []
  visited.clear()

  def topological_sort(target):
    if target not in visited:
      visited.add(target)
      if target in inverted_deps:
        for dep in inverted_deps[target]:
          topological_sort(dep)
      ordered.append(target)

  for root in roots:
    topological_sort(root)

  return ordered
