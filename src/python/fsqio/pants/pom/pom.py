# coding=utf-8
# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, print_function

from collections import defaultdict
from copy import deepcopy
import re
from xml.etree import ElementTree

from fsqio.pants.pom.coordinate import Coordinate
from fsqio.pants.pom.dependency import Dependency


class Pom(object):
  @staticmethod
  def resolve_placeholder(text, properties):
    def subfunc(matchobj):
      return properties.get(matchobj.group(1), matchobj.group(0))
    return re.sub(r'\$\{(.*?)\}', subfunc, text)

  @staticmethod
  def calculate_properties(coordinate, tree, parent_pom):
    default_properties = {
      'project.groupId': coordinate.groupId,
      'project.artifactId': coordinate.artifactId,
      'project.version': coordinate.version,
      'pom.groupId': coordinate.groupId,
      'pom.artifactId': coordinate.artifactId,
      'pom.version': coordinate.version,
      'groupId': coordinate.groupId,
      'artifactId': coordinate.artifactId,
      'version': coordinate.version,
    }
    declared_properties = {}
    properties_tree = tree.find('properties')
    if properties_tree is not None:
      for prop in properties_tree:
        if prop.tag == 'property':
          name = prop.get('name')
          value = prop.get('value') or ''
        else:
          name = prop.tag
          value = prop.text or ''
        declared_properties[name] = value

    properties = {}
    if parent_pom:
      properties.update(parent_pom.properties)
    properties.update(default_properties)
    properties.update(declared_properties)
    for key, val in properties.items():
      properties[key] = Pom.resolve_placeholder(val, properties)
    return properties

  @staticmethod
  def interpolate_properties(tree, properties):
    for elem in tree.iter():
      for attr_key in elem.keys():
        new_value = Pom.resolve_placeholder(elem.get(attr_key), properties)
        elem.set(attr_key, new_value)
      if elem.text is not None:
        elem.text = Pom.resolve_placeholder(elem.text, properties)
    return tree

  @staticmethod
  def resolve_dependency_management(tree, parent_pom):
    dep_management = defaultdict(dict)
    if parent_pom:
      dep_management.update(deepcopy(parent_pom.dependency_management))
    for dep_tree in tree.findall('dependencyManagement/dependencies/dependency'):
      key = (
        dep_tree.findtext('groupId'),
        dep_tree.findtext('artifactId'),
        dep_tree.findtext('type') or 'jar',
      )
      managed_dep = Dependency.attr_dict_from_tree(dep_tree)
      # NOTE: After writing this code to what I believed was the spec, I discovered
      # ch/qos/logback ... logback-parent-1.1.2.pom (and others, such as...)
      # org/apache/hadoop ... hadoop-project-2.0.0-cdh4.4.0.pom
      # Note dep management for ch.qos.logback:logback-core (in the logback pom) and
      # org.apache.hadoop:hadoop-common (in the hadoop pom) is there for BOTH the untyped
      # and the test-jar typed deps.  My logic treats the type as a more specific override,
      # so child poms that just ask for o.a.h:hadoop-common will get the test-jar type filled in,
      # which is almost certainly not what they want.  So maybe this should be keyed on
      # (groupId, artifactId, type) instead of just the first two.  For now the workaround is
      # simple: just manually bring in the jar that ends up missing.  See
      # jar(org = 'ch.qos.logback', name = 'logback-core', rev = '1.1.2') in 3rdparty/BUILD
      # for the concrete place where this bit us.
      dep_management[key].update(managed_dep)
      exclusions = dep_management[key].get('exclusions', [])
      for exclusion in dep_tree.findall('exclusions/exclusion'):
        groupId = exclusion.findtext('groupId')
        artifactId = exclusion.findtext('artifactId')
        exclusions.append((groupId, artifactId))
      dep_management[key]['exclusions'] = exclusions
    return dep_management

  @classmethod
  def resolve(cls, groupId, artifactId, version, fetcher):
    response = fetcher.get_pom(groupId, artifactId, version)
    pom_xml = response.content
    # See http://bugs.python.org/issue18304
    pom_xml = re.sub(r"<project(.|\s)*?>", '<project>', pom_xml, 1)
    tree = ElementTree.fromstring(pom_xml)

    parent = tree.find('parent')
    if parent is not None:
      parent_groupId = parent.findtext("groupId")
      parent_artifactId = parent.findtext("artifactId")
      parent_version = parent.findtext("version")
      parent_pom = cls.resolve(parent_groupId, parent_artifactId, parent_version, fetcher)
    else:
      parent_pom = None

    packaging = tree.findtext('packaging', default='jar')
    classifier = tree.findtext('classifier')
    coordinate = Coordinate(groupId, artifactId, version, packaging, classifier, fetcher.repo)
    properties = Pom.calculate_properties(coordinate, tree, parent_pom)
    tree = Pom.interpolate_properties(tree, properties)

    # We need to let property interpolation happen first, since the version might be
    # interpolated.  Otherwise we would check earlier.
    declared_groupId = tree.findtext('groupId')
    declared_artifactId = tree.findtext('artifactId')
    declared_version = tree.findtext('version')
    if parent_pom:
      if declared_groupId is None:
        declared_groupId = parent_pom.coordinate.groupId
      if declared_version is None:
        declared_version = parent_pom.coordinate.version
    fetched_coord = (groupId, artifactId, version)
    declared_coord = (declared_groupId, declared_artifactId, declared_version)
    if fetched_coord != declared_coord:
      raise ValueError(
        'Fetched Pom coordinate does not match coordinate declared within the pom.'
        '\nFetched: {}.\nDeclared: {}'
        .format(fetched_coord, declared_coord))

    dependency_management = Pom.resolve_dependency_management(tree, parent_pom)
    dependencies = {}
    if parent_pom:
      for dep in parent_pom.dependencies:
        dependencies[(dep.groupId, dep.artifactId, dep.classifier, dep.type)] = dep
    for dep_tree in tree.findall('dependencies/dependency'):
      dep = Dependency.from_xml(dep_tree, dependency_management)
      dependencies[(dep.groupId, dep.artifactId, dep.classifier, dep.type)] = dep

    return cls(
      tree=tree,
      coordinate=coordinate,
      parent_pom=parent_pom,
      properties=properties,
      dependency_management=dependency_management,
      dependencies=dependencies.values(),
    )

  def __init__(
    self,
    tree,
    coordinate,
    parent_pom,
    properties,
    dependency_management,
    dependencies,
  ):
    self.tree = tree
    self.coordinate = coordinate
    self.parent_pom = parent_pom
    self.properties = properties
    self.dependency_management = dependency_management
    self.dependencies = dependencies
