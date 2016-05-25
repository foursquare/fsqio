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
from copy import deepcopy
import errno
import functools
import hashlib
from itertools import chain, izip
import json
import logging
from multiprocessing import Pool
import os
import pickle
import sys
from uuid import uuid4
from xml.etree import ElementTree

from pants.backend.jvm.jar_dependency_utils import M2Coordinate, ResolvedJar
from pants.backend.jvm.targets.jar_library import JarLibrary
from pants.backend.jvm.tasks.classpath_products import ClasspathProducts
from pants.base.fingerprint_strategy import TaskIdentityFingerprintStrategy
from pants.base.payload_field import stable_json_sha1
from pants.base.specs import DescendantAddresses
from pants.invalidation.cache_manager import VersionedTargetSet
from pants.task.task import Task
from requests_futures.sessions import FuturesSession

from fsqio.pants.pom.coordinate import Coordinate
from fsqio.pants.pom.dependency import Dependency
from fsqio.pants.pom.fetcher import ChainedFetcher
from fsqio.pants.pom.maven_dependency_graph import MavenDependencyGraph
from fsqio.pants.pom.maven_version import MavenVersion, MavenVersionRangeRef
from fsqio.pants.pom.pom import Pom
from fsqio.pants.pom.sort_projects import CycleException
from fsqio.pants.util.dirutil import safe_mkdir


logger = logging.getLogger(__name__)

logging.getLogger('fsqio.pants.pom').setLevel(logging.WARN)


def stderr(*args, **kwargs):
  print(*args, file=sys.stderr, **kwargs)


def file_contents_equal(path1, path2):
  with open(path1, 'rb') as f1, open(path2, 'rb') as f2:
    megabyte = 2 ** 20
    while True:
      block1 = f1.read(megabyte)
      block2 = f2.read(megabyte)
      if block1 == b'' and block2 == b'':
        return True
      elif block1 != block2:
        return False


def traverse_project_graph(
  maven_dep,
  fetchers,
  global_pinned_versions,
  local_pinned_versions,
  global_exclusions,
  local_exclusions,
  unversioned_dep_chain=(),
):
  """Recursively walk the pom dependency graph for a given dep.

  Returns a MavenDependencyGraph instance.

  This algorithm is intentionally uncached since any truly correct implementation would have cache
  entries keyed on pins and exclusions, which very likely change depending on the path to the
  subgraph being resolved.  As a result, this is slow for large graphs--in fact I think it is
  exponential.  Every possible subgraph is resolved for every possible path to it from the root
  node.

  However, it only takes about 1 minute to run this for our entire universe of dependencies, so it's
  not optimized at all, just parallelizable, which is taken advantage of by its caller.  And this
  is only ever run when changing deps in 3rdparty/BUILD, which is rare enough to pay a minute
  for correctness.
  """
  logger.debug('Traversing graph for {}'.format(maven_dep))
  if maven_dep.unversioned_coordinate in unversioned_dep_chain:
    raise CycleException(unversioned_dep_chain + (maven_dep.unversioned_coordinate,))

  fetcher, pom_str = fetchers.resolve_pom(maven_dep.groupId, maven_dep.artifactId,
                                      maven_dep.version, jar_coordinate=maven_dep.coordinate)

  # TODO(mateo): This code block was not executed in almost 3 months - it should be deleted and instead raise an
  # exception requesting a pin.
  if '(' in maven_dep.version or '[' in maven_dep.version:
    # These range refs are fairly rare, and where they do happen we should probably just
    # be conservative and have a global pin of that dep.  Here, we just choose the latest
    # version available that matches the range spec.  We could also get fancier and have
    # an option dictating a strategy for choosing.
    content = fetcher.get_maven_metadata_xml(maven_dep.groupId, maven_dep.artifactId)
    tree = ElementTree.fromstring(content)
    all_versions = [v.text.strip() for v in tree.findall('versioning/versions/version')]
    latest = tree.findtext('versioning/latest').strip()
    release = tree.findtext('versioning/release').strip()
    range_ref = MavenVersionRangeRef(maven_dep.version)
    maven_versions = [MavenVersion(version_str) for version_str in all_versions]
    matching_versions = [mv for mv in maven_versions if range_ref.matches(mv)]
    not_qualified_versions = [mv for mv in matching_versions if mv.qualifier is None]
    maven_dep = maven_dep._replace(version=not_qualified_versions[-1]._version_str)

  dep_graph = MavenDependencyGraph()
  dep_graph.ensure_node(maven_dep.coordinate)

  if fetcher is None:
    raise MissingResourceException('Failed to fetch maven pom or resource: {}'.format(maven_dep.coordinate))
  if pom_str is None:
    logger.debug(
      'Pom did not exist for dependency: {}.\n'
      'But a resource matching that spec did, treating it as an intransitive resource.'
      .format(maven_dep))
    dep_graph.add_provided_artifacts(maven_dep.coordinate, fetcher.repo, [maven_dep.coordinate])
    return dep_graph

  logger.debug('{} found by fetcher: {}.\n' .format(maven_dep.coordinate, fetcher.name))
  pom = Pom.resolve(maven_dep.groupId, maven_dep.artifactId, maven_dep.version, fetcher)

  if pom.coordinate.packaging == 'pom':
    # Check to see if this is an aggregation pom that happens to also provide a jar.
    # Ivy in this circumstance will treat that jar as an artifact of the project, but
    # if the jar isn't there it doesn't consider it an error.

    jar_coord = pom.coordinate._replace(packaging='jar')
    if fetcher.resource_exists(jar_coord):
      dep_graph.add_provided_artifacts(maven_dep.coordinate, fetcher.repo, [jar_coord])
    else:
      dep_graph.add_provided_artifacts(maven_dep.coordinate, fetcher.repo, [])
  elif pom.coordinate.packaging in ('bundle', 'jar'):
    # Always treat this as a jar
    if maven_dep.type != 'jar':
      raise Exception(
        'When resolving {}, the pom packaging was "{}", which does not match the'
        ' requested dep type ("{}")'
        .format(maven_dep, pom.coordinate.packaging, maven_dep.type))
    dep_graph.add_provided_artifacts(maven_dep.coordinate, fetcher.repo, [maven_dep.coordinate])
  else:
    raise Exception('Unsupported pom packaging type: {}'.format(pom.coordinate))

  if maven_dep.intransitive:
    return dep_graph

  for dep in pom.dependencies:
    if dep.scope not in ('compile', 'runtime'):
      logger.debug(
        'Skipping dependency {} of {} because its scope is "{}".'
        .format(dep, maven_dep, dep.scope))
      continue
    if dep.optional:
      logger.debug(
        'Skipping dependency {} of {} because it is optional.'
        .format(dep, maven_dep))
      continue
    if dep.unversioned_coordinate in global_exclusions:
      logger.debug(
        'Skipping dependency {} of {} because it is in the global exclusions list.'
        .format(dep, maven_dep))
      dep_graph.used_global_exclude(dep.unversioned_coordinate)
      continue
    if dep.unversioned_coordinate in local_exclusions:
      logger.debug(
        'Skipping dependency {} of {} because it is in the local exclusions list.'
        .format(dep, maven_dep))
      continue
    if dep.type == 'test-jar' and dep.classifier:
      raise Exception(
        'While resolving maven dep {}, saw dep {} with type test-jar in scope "{}",'
        ' but with a classifier.  The classifier should be empty so we can rewrite'
        ' the dep to have classifier "tests".  Chain to top: {}'
        .format(maven_dep, dep, dep.scope, unversioned_dep_chain)
      )
    if dep.type == 'test-jar':
      logger.debug(
        'Rewriting dependency {} to have classifier "tests" and type "jar" since it is'
        ' requesting a "test-jar" with supported scope "{}".'
        .format(dep, dep.scope)
      )
      dep = dep._replace(classifier='tests', type='jar')

    if dep.unversioned_coordinate in global_pinned_versions:
      dep = dep._replace(version=global_pinned_versions[dep.unversioned_coordinate])
      dep_graph.used_global_pin(dep.unversioned_coordinate)
    elif local_pinned_versions.get(dep.unversioned_coordinate) is not None:
      dep = dep._replace(version=local_pinned_versions.get(dep.unversioned_coordinate))

    dep_graph.add_dependency(
      dependee_coord=maven_dep.coordinate,
      dependency=dep,
    )

    new_local_pinned_versions = {}
    new_local_pinned_versions.update(local_pinned_versions)
    for key, managed_dep in pom.dependency_management.items():
      if 'version' in managed_dep:
        pinned_version = managed_dep['version']
        if key in new_local_pinned_versions and new_local_pinned_versions[key] != pinned_version:
          logger.debug(
            'Conflicting pinned versions of {}: {} vs {}.  Removing the pin and moving on.'
            .format(key, pinned_version, new_local_pinned_versions[key]))
          new_local_pinned_versions[key] = None
        else:
          logger.debug(
            '{} used dep management to pin {} to {}'
            .format(pom.coordinate, key, pinned_version))
          new_local_pinned_versions[key] = pinned_version

    child_dep_graph = traverse_project_graph(
      maven_dep=dep,
      fetchers=fetchers,
      global_pinned_versions=global_pinned_versions,
      local_pinned_versions=new_local_pinned_versions,
      global_exclusions=global_exclusions,
      local_exclusions=frozenset(dep.exclusions | local_exclusions),
      unversioned_dep_chain=unversioned_dep_chain + (maven_dep.unversioned_coordinate,),
    )
    dep_graph.merge(child_dep_graph)
  return dep_graph


def resolve_maven_deps(args):
  """Resolves the graph for each maven dependency, merges them, and returns the merged result.

  This is intended to be run within a multiprocessing worker pool, so all inputs and outputs
  are pickleable and the args are a single argument for convenience of calling `Pool.imap`.
  """
  maven_deps, fetchers, global_pinned_versions, global_exclusions = args
  dep_graph = MavenDependencyGraph()
  for maven_dep in maven_deps:
    local_dep_graph = traverse_project_graph(
      maven_dep,
      fetchers,
      global_pinned_versions=global_pinned_versions,
      local_pinned_versions={},
      global_exclusions=global_exclusions,
      local_exclusions=maven_dep.exclusions,
    )
    dep_graph.merge(local_dep_graph)
  sys.stderr.write('.')
  return dep_graph


class MissingResourceException(Exception):
  """Indicates that no resource matching that maven coordinate was found."""


class PomResolveFingerprintStrategy(TaskIdentityFingerprintStrategy):
  """A FingerprintStrategy with the addition of global exclusions and pins."""

  def __init__(self, global_exclusions=None, global_pinned_versions=None):
    self._extra_fingerprint_digest = stable_json_sha1([
      sorted(list(global_exclusions or [])),
      {str(key): val for key, val in (global_pinned_versions or {}).items()},
    ])

  def compute_fingerprint(self, target):
    hasher = hashlib.sha1()
    hasher.update(target.payload.fingerprint())
    hasher.update(self._extra_fingerprint_digest)
    return hasher.hexdigest()

  def __hash__(self):
    return hash((type(self), self._extra_fingerprint_digest))

  def __eq__(self, other):
    return (
      type(self) == type(other) and
      self._extra_fingerprint_digest == other._extra_fingerprint_digest
    )


class PomResolve(Task):

  @classmethod
  def register_options(cls, register):
    super(PomResolve, cls).register_options(register)
    register(
      '--cache-dir',
      default='~/.pom2',
      advanced=True,
      help='Cache resolved pom artifacts in this directory.',
    )
    register(
      '--global-exclusions',
      type=list,
      member_type=dict,
      advanced=True,
      help='A list of dicts representing coordinates { org: a, name: b } '
           'to exclude globally from consideration.',
    )
    register(
      '--global-pinned-versions',
      type=list,
      member_type=dict,
      advanced=True,
      help='A list of dicts representing coordinates { org: a, name: b, rev: x.y.z } to '
           'explicitly pin during resolution.',
    )
    register(
      '--local-override-versions',
      type=list,
      member_type=dict,
      advanced=True,
      help='A map of dicts coordinates { { org: a, name: b, rev: x.y.z } : "path/to/artifact.jar" } to a path string, '
           'allowing the use of particular local jars.',
    )
    register(
      '--report-artifacts',
      type=list,
      default=None,
      help='An "org:name" to print detailed resolution about when working with a new classpath.'
    )
    register(
      '--fetch-source-jars',
      type=bool,
      default=False,
      help='Fetch source jars and symlink them into the pom cache dir.  Prints the directory where'
           ' the source jar symlink farm resides.'
    )
    register(
      '--dump-classpath-file',
      type=str,
      default=None,
      help='Dump a text file of the entire global classpath to the specified file.'
    )
    register(
      '--write-classpath-info-to-file',
      default=None,
      help='Write JSON describing the global classpath to the specified file.',
    )
    register(
      '--maven-repos',
      type=list,
      member_type=dict,
      advanced=False,
      help='A list of maps {name: url} that point to maven-style repos, preference is left-to-right.',
    )

  @classmethod
  def prepare(cls, options, round_manager):
    super(PomResolve, cls).prepare(options, round_manager)
    # Since we populate the compile_classpath product in terms of individual target closures,
    # we need to make sure that codegen has run and injected the synthetic dependencies on
    # runtime jars, e.g. the spindle runtime.
    # This does not affect actual resolution, only the product population that happens every
    # run.
    round_manager.require_data('java')
    round_manager.require_data('scala')

  @classmethod
  def product_types(cls):
    return [
      'compile_classpath',
      'coord_to_artifact_symlinks',
      'ivy_cache_dir',
      'ivy_jar_products',
      'ivy_resolve_symlink_map',
      'jar_dependencies',
      'maven_coordinate_to_provided_artifacts',
      'target_to_maven_coordinate_closure',
    ]

  @property
  def target_to_maven_coordinate_closure(self):
    return self.context.products.get_data('target_to_maven_coordinate_closure', lambda: {})

  @property
  def maven_coordinate_to_provided_artifacts(self):
    return self.context.products.get_data('maven_coordinate_to_provided_artifacts', lambda: {})

  @property
  def artifact_symlink_dir(self):
    return os.path.join(self.workdir, 'syms')

  @property
  def pom_cache_dir(self):
    return os.path.expanduser(self.get_options().cache_dir)

  def resolve_dependency_graphs(self, all_jar_libs, fetchers, global_exclusions, global_pinned_versions):
    """Resolve a {jar_lib: subgraph} map and a merged global graph for all jar libs."""

    global_pinned_versions = deepcopy(global_pinned_versions)
    # Double check that our pins aren't conflicting, and globally pin the version of every
    # dep explicitly requested.
    for jar_lib in all_jar_libs:
      for jar_dep in jar_lib.jar_dependencies:
        if (jar_dep.org, jar_dep.name) in global_pinned_versions:
          if global_pinned_versions[(jar_dep.org, jar_dep.name)] != jar_dep.rev:
            raise Exception(
              'Conflicting top level version/pin requested: {}'
              .format((jar_dep.org, jar_dep.name)))
        else:
          global_pinned_versions[(jar_dep.org, jar_dep.name)] = jar_dep.rev

    resolve_maven_deps_args = []
    for jar_lib in all_jar_libs:
      maven_deps = []
      for jar_dep in jar_lib.jar_dependencies:
        maven_deps.append(Dependency(
          groupId=jar_dep.org,
          artifactId=jar_dep.name,
          version=jar_dep.rev,
          exclusions=frozenset((exclude.org, exclude.name) for exclude in jar_dep.excludes),
          classifier=jar_dep.classifier,
          type='jar',
          scope='compile',
          systemPath=None,
          optional=False,
        ))
      resolve_maven_deps_args.append((maven_deps, fetchers, global_pinned_versions, global_exclusions))

    def resolve_deps_in_process():
      for args in resolve_maven_deps_args:
        yield resolve_maven_deps(args)

    pool = Pool()
    # PROTIP: Use the in_process dep_graph_iterator to debug.
    dep_graph_iterator = pool.imap(resolve_maven_deps, resolve_maven_deps_args)
    #dep_graph_iterator = resolve_deps_in_process()

    global_dep_graph = MavenDependencyGraph()
    target_to_dep_graph = {}
    for jar_lib, target_dep_graph in izip(all_jar_libs, dep_graph_iterator):
      target_to_dep_graph[jar_lib] = target_dep_graph
      global_dep_graph.merge(target_dep_graph)
    return global_dep_graph, target_to_dep_graph

  def print_graph(self, artifact, reverse_unversioned_dep_graph, visited=None, depth=0):
    visited = visited or set()
    if artifact in visited:
      stderr('\t' * depth, str(artifact), '*')
    else:
      stderr('\t' * depth, str(artifact))
      visited.add(artifact)
      for dependee in reverse_unversioned_dep_graph[artifact]:
        self.print_graph(dependee, reverse_unversioned_dep_graph, visited, depth + 1)

  def report_conflicted_deps(
    self,
    conflicted_dep_tuples,
    reverse_unversioned_dep_graph,
    dep_graph,
  ):
    """Report helpful information about dep in the global graph that have version conflicts."""
    # TODO: Topologically sort the conflicted deps, and optionally only present the most
    # dependent deps that can be pinned.  Notably, do not present conflicted deps that are
    # dependencies of other conflicted deps, since the dependencies' conflicts might disappear
    # once the dependee is pinned.
    # I have found that this strategy of working through conflicts is the maximally efficient
    # way to pin as many things as possible in a given resolution run while not pinning
    # anything unnecessarily.
    stderr('\nFound {} dependencies with conflicting revs:\n'
          .format(len(conflicted_dep_tuples)))
    for coord, revs in conflicted_dep_tuples:
      stderr('{}:'.format(coord))
      for rev in sorted(revs):
        stderr('\t* {}'.format(rev))
      stderr('\nDependees of {}'.format(coord))
      for dependee in reverse_unversioned_dep_graph[coord]:
        stderr('\t- {}'.format(dependee))
      stderr('\n\n')
    stderr('\n')

  def report_unused_pins_and_exclusions(self, dep_graph, global_pinned_versions, global_exclusions):
    unused_pins = set(global_pinned_versions.keys()) - dep_graph._used_pins
    if unused_pins:
      stderr('\nThe following globally pinned artifacts were unused:')
      for org, name in sorted(unused_pins):
        stderr('\t* {}:{}'.format(org, name))
    unused_exclusions = set(global_exclusions) - dep_graph._used_global_excludes
    if unused_exclusions:
      stderr('\nThe following globally excluded artifacts were unused:')
      for org, name in sorted(unused_exclusions):
        stderr('\t* {}:{}'.format(org, name))

  def report_for_artifacts(self, global_dep_graph):
    """Print detailed classifier/type and dependee/dependency graphs for flagged coords."""

    reverse_unversioned_dep_graph = global_dep_graph.reverse_unversioned_dep_graph()
    unversioned_dep_graph = global_dep_graph.unversioned_dep_graph()
    for artifact_str in self.get_options().report_artifacts:
      org, name = artifact_str.split(':')
      matching_coords = set(
        coord for coord in global_dep_graph._coord_to_versioned_deps.keys()
        if (coord.groupId, coord.artifactId) == (org, name)
      )
      stderr(
        '\nThe following coordinates matched this org:name ({}:{}), (note classifiers and types)'
        .format(org, name)
      )
      for coord in matching_coords:
        stderr('\n{}'.format(coord))
        stderr('\nDependency graph for {}'.format(artifact_str))
        self.print_graph(coord, unversioned_dep_graph)
        stderr('\nDependee (reversed arrows) graph for {}'.format(artifact_str))
        self.print_graph(coord, reverse_unversioned_dep_graph)
        stderr('\n')

  def check_artifact_cache_for(self, invalidation_check):
    """Tells the artifact cache mechanism that we have a single artifact per global classpath."""
    global_vts = VersionedTargetSet.from_versioned_targets(invalidation_check.all_vts)
    return [global_vts]

  _all_jar_libs = None
  @property
  def all_jar_libs(self):
    # NOTE: We always operate over 3rdparty::.  This is somewhat arbitrary and could instead
    # live in configuration, but for now it is so universal that I will leave it hard coded.
    # Note that since we don't mess with the actual target roots here, there should be no
    # side effects downstream from injecting more targets in the build graph (they will
    # either be unconnected nodes of the target root graph or they will have been pulled
    # in anyway)
    if self._all_jar_libs is None:
      build_graph = self.context.build_graph
      third_party_libs = set()
      for address in self.context.address_mapper.scan_specs([DescendantAddresses('3rdparty')]):
        build_graph.inject_address_closure(address)
        third_party_libs.add(build_graph.get_target(address))
      self._all_jar_libs = set(t for t in third_party_libs if isinstance(t, JarLibrary))
    return self._all_jar_libs

  def execute(self):

    # Pants does no longer allows options to be tuples or sets. So we use lists of dicts and then convert into
    # hashable structures here.

    # Pins converted to { (org, name): rev, ... }
    global_pinned_tuples = {}
    for pin in self.get_options().global_pinned_versions:
      artifact_tuple = (pin['org'], pin['name'])
      if artifact_tuple in global_pinned_tuples:
        raise Exception('An artifact has conflicting overrides!:\n{}:{} and\n'
          '{}'.format(artifact_tuple, pin['rev'], global_pinned_tuples[artifact_tuple]))
      global_pinned_tuples[artifact_tuple] = pin['rev']

    # Overrrides converted to { (org, name, rev): /path/to/artifact, ... }
    override_tuples = {}
    for override in self.get_options().local_override_versions:
      override_tuples[(override['org'], override['name'], override['rev'])] = override['artifact_path']

    # Exclusions converted to [(org, name), ...]
    global_exclusion_tuples = []
    for exclusion in self.get_options().global_exclusions:
      global_exclusion_tuples.append((exclusion['org'], exclusion['name']))

    global_exclusions = frozenset(global_exclusion_tuples)
    global_pinned_versions = dict(global_pinned_tuples)
    local_override_versions = override_tuples
    fetchers = ChainedFetcher(self.get_options().maven_repos)

    invalidation_context_manager = self.invalidated(
      self.all_jar_libs,
      invalidate_dependents=False,
      fingerprint_strategy=PomResolveFingerprintStrategy(global_exclusions, global_pinned_versions),
    )

    with invalidation_context_manager as invalidation_check:
      # NOTE: In terms of caching this models IvyResolve in pants quite closely. We always
      # operate over and cache in terms of the global set of jar dependencies. Note that we override
      # `check_artifact_cache_for` in order to get the artifact cache to respect this.
      global_vts = VersionedTargetSet.from_versioned_targets(invalidation_check.all_vts)
      vts_workdir = os.path.join(self.workdir, global_vts.cache_key.hash)
      analysis_path = os.path.join(vts_workdir, 'analysis.pickle')
      if invalidation_check.invalid_vts or not os.path.exists(analysis_path):
        with self.context.new_workunit('traverse-pom-graph'):
          global_dep_graph, target_to_dep_graph = self.resolve_dependency_graphs(
            self.all_jar_libs,
            fetchers,
            global_exclusions,
            global_pinned_versions,
          )
          self.report_unused_pins_and_exclusions(
            global_dep_graph,
            global_pinned_versions,
            global_exclusions,
          )
        # TODO: Not super happy about using target.id really anywhere, since it's just a name.
        # But for now this is all completely invalidated whenever any part of 3rdparty:: changes.
        # It might however be possible that just renaming a JarLib (and doing nothing else) will
        # break this.
        for target, dep_graph in target_to_dep_graph.items():
          self.target_to_maven_coordinate_closure[target.id] = list(dep_graph.artifact_closure())
        copied_coord_to_artifacts = deepcopy(global_dep_graph._coord_to_provided_artifacts)
        self.maven_coordinate_to_provided_artifacts.update(copied_coord_to_artifacts)
        safe_mkdir(vts_workdir)
        # NOTE: These products are only used by pom-ivy-diff, which is only there for debugging.
        # It will probably go away within a few months, at which point these products optionally
        # can too.  But they might also be useful to future downstream tasks.
        analysis = {
          'target_to_maven_coordinate_closure': self.target_to_maven_coordinate_closure,
          'maven_coordinate_to_provided_artifacts': self.maven_coordinate_to_provided_artifacts,
          'global_dep_graph': global_dep_graph,
        }
        with open(analysis_path, 'wb') as f:
          pickle.dump(analysis, f)
        if self.artifact_cache_writes_enabled():
          self.update_artifact_cache([(global_vts, [analysis_path])])
      else:
        with open(analysis_path, 'rb') as f:
          analysis = pickle.load(f)
        self.target_to_maven_coordinate_closure.update(
          analysis['target_to_maven_coordinate_closure'],
        )
        self.maven_coordinate_to_provided_artifacts.update(
          analysis['maven_coordinate_to_provided_artifacts'],
        )
        global_dep_graph = analysis['global_dep_graph']

    self.report_for_artifacts(global_dep_graph)
    conflicted_deps = global_dep_graph.conflicted_dependencies()
    if conflicted_deps:
      self.report_conflicted_deps(
        conflicted_deps,
        global_dep_graph.reverse_unversioned_dep_graph(),
        global_dep_graph,
      )
      raise Exception(
        'PomResolve found {} conflicting dependencies.  These must be explicitly'
        ' pinned or excluded in order to generate a consistent global classpath.'
        ' See the output above for details, and try `./pants pom-resolve --help`'
        ' for information on flags to get more detailed reporting.'
        .format(len(conflicted_deps)))

    all_artifacts = set()
    for coord_closure in self.target_to_maven_coordinate_closure.values():
      for coord in coord_closure:
        for artifact in self.maven_coordinate_to_provided_artifacts[coord]:
          all_artifacts.add(artifact)

    classpath_dump_file = self.get_options().dump_classpath_file
    if classpath_dump_file:
      with open(classpath_dump_file, 'wb') as f:
          f.write('FINGERPRINT: {}\n'.format(global_vts.cache_key.hash))
          for artifact in sorted(all_artifacts):
            f.write('{}\n'.format(artifact))
      logger.info('Dumped classpath file to {}'.format(classpath_dump_file))

    with self.context.new_workunit('fetch-artifacts'):
      coord_to_artifact_symlinks = self._fetch_artifacts(local_override_versions)

    if self.get_options().fetch_source_jars:
      with self.context.new_workunit('fetch-source-jars'):
        symlink_dir = os.path.join(
          self.pom_cache_dir,
          'source-jars-symlink-farms',
          global_vts.cache_key.hash,
        )
        if not os.path.exists(symlink_dir):
          self._fetch_source_jars(fetchers, symlink_dir)
        stderr('\nFetched source jars to {}'.format(symlink_dir))

    classpath_info_filename = self.get_options().write_classpath_info_to_file
    if classpath_info_filename:
      classpath_info = {
        'fingerprint': global_vts.cache_key.hash,
        'classpath': [
           {
             'path': os.path.join(self.pom_cache_dir, artifact.artifact_path),
             'groupId': artifact.groupId,
             'artifactId': artifact.artifactId,
             'version': artifact.version,
             'packaging': artifact.packaging,
             'classifier': artifact.classifier,
           }
           for artifact in all_artifacts
        ],
      }
      with open(classpath_info_filename, 'w') as classpath_info_file:
        classpath_info_file.write(json.dumps(classpath_info))
      logger.info('Wrote classpath info JSON to {}.'.format(classpath_info_filename))

    with self.context.new_workunit('populate-compile-classpath'):
      self._populate_compile_classpath()

  def _populate_compile_classpath(self):
    products = self.context.products
    compile_classpath = products.get_data('compile_classpath',
                                          init_func=ClasspathProducts.init_func(self.get_options().pants_workdir))
    sorted_targets = sorted(
      self.context.targets(predicate=lambda t: t in self.all_jar_libs),
      key=lambda t: t.address.spec,
    )
    for target in sorted_targets:
      resolved_jars = []
      for coord in sorted(self.target_to_maven_coordinate_closure[target.id]):
        m2_coord = M2Coordinate(
          org=coord.groupId,
          name=coord.artifactId,
          rev=coord.version,
          classifier=coord.classifier,
          ext=coord.packaging,
        )
        sorted_artifact_paths = sorted(
          artifact.artifact_path
          for artifact in self.maven_coordinate_to_provided_artifacts[coord]
        )
        for artifact_path in sorted_artifact_paths:
          resolved_jar = ResolvedJar(
            coordinate=m2_coord,
            pants_path=os.path.join(self.artifact_symlink_dir, artifact_path),
            cache_path=os.path.join(self.pom_cache_dir, artifact_path),
          )
          resolved_jars.append(resolved_jar)
      compile_classpath.add_jars_for_targets([target], 'default', resolved_jars)

  def _fetch_source_jars(self, fetchers, symlink_dir):
    future_session = FuturesSession(max_workers=4)
    coords = set(
      Coordinate(*t)
      for t in chain.from_iterable(self.target_to_maven_coordinate_closure.values())
    )
    artifacts_to_symlink = set()
    artifacts_to_download = set()
    with self.context.new_workunit('find-source-jars'):
      for coord in coords:
        for artifact in self.maven_coordinate_to_provided_artifacts[coord]:
          source_jar = artifact._replace(classifier='sources')
          cached_source_jar_path = os.path.join(self.pom_cache_dir, source_jar.artifact_path)
          already_downloaded = os.path.exists(cached_source_jar_path)
          artifacts_to_symlink.add(artifact)
          if not already_downloaded:
            # TODO(mateo): This probably should be a ChainedFetcher method instead of iterating over fetchers here.
            for fetcher in fetchers:
              if fetcher.resource_exists(source_jar):
                source_jar = source_jar._replace(repo_url=fetcher.repo)
                artifacts_to_symlink.add(source_jar)
                artifacts_to_download.add(source_jar)
                break
          else:
            artifacts_to_symlink.add(source_jar)

    with self.context.new_workunit('download-source-jars'):
      self._download_artifacts(artifacts_to_download)
    with self.context.new_workunit('symlink-source-jars'):
      safe_mkdir(symlink_dir)
      for artifact in artifacts_to_symlink:
        cached_artifact_path = os.path.join(self.pom_cache_dir, artifact.artifact_path)
        symlinked_artifact_path = os.path.join(
          symlink_dir,
          artifact.artifact_path.replace('/', '_'),
        )
        safe_mkdir(os.path.dirname(symlinked_artifact_path))
        try:
          os.symlink(cached_artifact_path, symlinked_artifact_path)
        except OSError as e:
          if e.errno != errno.EEXIST:
            stderr(
              'Failed to link artifact {} to the symlink farm at {}'
              .format(cached_artifact_path, symlinked_artifact_path))
            raise

  def _background_stream(self, artifact, session, response):
    response.raise_for_status()
    cached_artifact_path = os.path.join(self.pom_cache_dir, artifact.artifact_path)
    temp_path = '{}-{}'.format(cached_artifact_path, uuid4())
    safe_mkdir(os.path.dirname(cached_artifact_path))
    with open(temp_path, 'wb') as f:
      for chunk in response.iter_content(4096):
        f.write(chunk)
    if os.path.lexists(cached_artifact_path):
      if not file_contents_equal(temp_path, cached_artifact_path):
        raise Exception(
          'About to rename downloaded artifact {} from {} to {}, but the destination path'
          ' already exists and has different contents.'
          .format(artifact, temp_path, cached_artifact_path))
    else:
      os.rename(temp_path, cached_artifact_path)

  def _download_artifacts(self, artifacts):
    future_session = FuturesSession(max_workers=4)
    response_futures = []
    for artifact in artifacts:
      artifact_url = '/'.join([artifact.repo_url, artifact.artifact_path])
      streaming_callback = functools.partial(self._background_stream, artifact)
      response_future = future_session.get(
        artifact_url,
        stream=True,
        background_callback=streaming_callback,
      )
      response_future._artifact = artifact
      response_futures.append(response_future)

    # Trigger exceptions raised by background workers
    for future in response_futures:
      try:
        future.result()
      except Exception as e:
        print("Failed to download artifact: {}".format(future._artifact))
        raise e
      sys.stderr.write('.')

  def _fetch_artifacts(self, local_override_versions):
    """Download jars from maven repo into the artifact cache dir, then symlink them into our workdir."""

    products = self.context.products
    # Coordinate -> set(relative path to symlink of artifact in symlink farm)
    coord_to_artifact_symlinks = defaultdict(set)
    # Demanded by some downstream tasks
    products.safe_create_data('ivy_cache_dir', lambda: self.pom_cache_dir)
    coords = set(
      Coordinate(*t)
      for t in chain.from_iterable(self.target_to_maven_coordinate_closure.values())
    )
    artifacts_to_download = set()
    for coord in coords:
      for artifact in self.maven_coordinate_to_provided_artifacts[coord]:
        # Sanity check. At this point, all artifacts mapped to a coord should be fully resolved, location included.
        if artifact.repo_url is None:
          raise Exception("Something went wrong! {} was mapped to an artifact {} with no "
                          "associated repo: ".format(coord, artifact))
        cached_artifact_path = os.path.join(self.pom_cache_dir, artifact.artifact_path)
        if not os.path.exists(cached_artifact_path):
          artifacts_to_download.add(artifact)
    self._download_artifacts(artifacts_to_download)

    ivy_symlink_map = self.context.products.get_data('ivy_resolve_symlink_map', dict)
    for coord in coords:
      for artifact in self.maven_coordinate_to_provided_artifacts[coord]:
        local_override_key = (artifact.groupId, artifact.artifactId, artifact.version)
        if local_override_key not in local_override_versions:
          cached_artifact_path = os.path.realpath(os.path.join(self.pom_cache_dir, artifact.artifact_path))
        else:
          cached_artifact_path = os.path.realpath(local_override_versions[local_override_key])
          if not os.path.exists(cached_artifact_path):
            raise Exception('Local override for {} at {} does not exist.'.format(artifact, cached_artifact_path))

        symlinked_artifact_path = os.path.join(self.artifact_symlink_dir, artifact.artifact_path)
        safe_mkdir(os.path.dirname(symlinked_artifact_path))

        try:
          os.symlink(cached_artifact_path, symlinked_artifact_path)
        except OSError as e:
          if e.errno != errno.EEXIST:
            raise
          existing_symlink_target = os.readlink(symlinked_artifact_path)
          if existing_symlink_target != cached_artifact_path:
            raise Exception(
              'A symlink already exists for artifact {}, but it points to the wrong path.\n'
              'Symlink: {}\n'
              'Destination of existing symlink: {}\n'
              'Where this symlink should point: {}\n'
              .format(
                artifact,
                symlinked_artifact_path,
                existing_symlink_target,
                cached_artifact_path))
        ivy_symlink_map[cached_artifact_path] = symlinked_artifact_path
        coord_to_artifact_symlinks[artifact] = symlinked_artifact_path
    return coord_to_artifact_symlinks
