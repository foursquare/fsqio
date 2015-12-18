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
import os
import re

from pants.backend.jvm.targets.jar_library import JarLibrary
from pants.build_graph.address import Address
from pants.task.task import Task


logger = logging.getLogger(__name__)


class PomIvyDiff(Task):
  """Report the difference in ivy vs pom resolution for a given target.

  In order to use this, you must comment out the product population in PomResolve
  (_populate_ivy_jar_products and _mapjars).  You must also comment out the unregistration
  of ivy in register.py.
  """

  @classmethod
  def register_options(cls, register):
    super(PomIvyDiff, cls).register_options(register)
    # NOTE: I would love to infer this from the target roots, but the target to get ivy jar
    # products for must be requested in `prepare`.  I have thought of ways to get around this,
    # but they are unspeakably evil and a little extra configuration won't hurt anyone.
    register(
      '--diffed-target-specs',
      action='append',
      default=None,
      help='A target spec to get ivy report info from and diff with the pom resolve results.'
           ' The spec must appear as well as a regular target argument to the goal, or'
           ' in the transitive closure thereof.  An idiom for common use of this is'
           ' `./pants pom-ivy-diff --diffed-target-specs=foo/bar foo/bar`.',
    )

  @classmethod
  def prepare(cls, options, round_manager):
    super(PomIvyDiff, cls).prepare(options, round_manager)
    round_manager.require_data('target_to_maven_coordinate_closure')
    round_manager.require_data('maven_coordinate_to_provided_artifacts')
    round_manager.require_data('ivy_jar_products')

    diffed_target_addresses = set(
      Address.parse(spec)
      for spec in options.diffed_target_specs
    )
    def target_jar_deps_required(target):
      if diffed_target_addresses:
        return target.address in diffed_target_addresses
      else:
        return isinstance(target, JarLibrary)
    round_manager.require('jar_dependencies', predicate=target_jar_deps_required)

  def execute(self):
    build_graph = self.context.build_graph
    diffed_target_addresses = set(
      Address.parse(spec)
      for spec in self.get_options().diffed_target_specs
    )
    if diffed_target_addresses:
      targets_to_diff = [build_graph.get_target(address) for address in diffed_target_addresses]
    else:
      targets_to_diff = self.context.targets(predicate=lambda t: isinstance(t, JarLibrary))

    for target in targets_to_diff:
      self.report_target_diff(target)

  def report_target_diff(self, target):
    products = self.context.products
    build_graph = self.context.build_graph
    genmap = self.context.products.get('jar_dependencies')
    target_to_maven_coordinate_closure = products.get_data('target_to_maven_coordinate_closure')
    maven_coordinate_to_provided_artifacts = products.get_data(
      'maven_coordinate_to_provided_artifacts'
    )
    ivy_jar_products = products.get_data('ivy_jar_products')

    ivy_info = ivy_jar_products['default'][0]
    IVY_ARTIFACT_RE = os.sep.join([
      r'.*?\.ivy2/cache',
      r'(?P<org>[^/]+)',
      r'(?P<name>[^/]+)',
      r'(?:jars|bundles)',
      r'(?P=name)-(?P<rev>[^/]+){classifier}\.(?P<ext>.*?)$',
    ])

    jars_to_diff = set(
      t for t in build_graph.transitive_subgraph_of_addresses([target.address])
      if isinstance(t, JarLibrary)
    )

    pom_used_coords = {}
    ivy_resolved_coords = {}
    for jar_lib in jars_to_diff:
      for coord in target_to_maven_coordinate_closure[jar_lib.id]:
        pom_used_coords[(coord.groupId, coord.artifactId, coord.classifier)] = coord.version
      for path, classifier in ivy_info.get_artifacts_for_jar_library(jar_lib):
        classifier_str = '-{}'.format(classifier) if classifier else ''
        regex = re.compile(IVY_ARTIFACT_RE.format(classifier=classifier_str))
        match = regex.match(path)
        if not match:
          print(path)
          import ipdb; ipdb.set_trace()
          print()
        key = (
          match.groupdict()['org'],
          match.groupdict()['name'],
          classifier,
        )
        ivy_resolved_coords[key] = match.groupdict()['rev']

    ivy_only_keys = sorted(set(ivy_resolved_coords.keys()) - set(pom_used_coords.keys()))
    pom_only_keys = sorted(set(pom_used_coords.keys()) - set(ivy_resolved_coords.keys()))
    if ivy_only_keys or pom_only_keys:
      print('\n\n')
      print(target.address.spec)
    if ivy_only_keys:
      print('Deps brought in by ivy but not pom-resolve:')
      for org, name, classifier in ivy_only_keys:
        print(
          '\t* {}:{} [{}] ({})'
          .format(org, name, classifier, ivy_resolved_coords[(org, name, classifier)])
        )
      print('\n\n')

    if pom_only_keys:
      print('Deps brought in by pom-resolve but not ivy:')
      for org, name, classifier in pom_only_keys:
        print(
          '\t* {}:{} [{}] ({})'
          .format(org, name, classifier, pom_used_coords[(org, name, classifier)])
        )
      print('\n\n')

    shared_keys = sorted(set(ivy_resolved_coords.keys()) & set(pom_used_coords.keys()))
    differing_version_keys = set()
    for key in shared_keys:
      ivy_rev = ivy_resolved_coords[key]
      pom_rev = pom_used_coords[key]
      if ivy_rev != pom_rev:
        differing_version_keys.add(key)
    if differing_version_keys:
      print('Deps with different versions:')
      for org, name, classifier in sorted(differing_version_keys):
        key = (org, name, classifier)
        ivy_rev = ivy_resolved_coords[key]
        pom_rev = pom_used_coords[key]
        print(
          "\t* {org}:{name} [{classifier}]\n"
          "\t\tIVY: ('{org}', '{name}'): '{ivy_rev}'\n"
          "\t\tPOM: ('{org}', '{name}'): '{pom_rev}'\n\n"
          .format(org=org, name=name, classifier=classifier, ivy_rev=ivy_rev, pom_rev=pom_rev)
        )
