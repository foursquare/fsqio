# coding=utf-8
# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function

from collections import OrderedDict
from difflib import unified_diff
import json
import os

from pants.backend.jvm.ivy_utils import (
  IvyFetchStep,
  IvyResolveStep,
  IvyUtils,
  NO_RESOLVE_RUN_RESULT,
)
from pants.backend.jvm.subsystems.jar_dependency_management import (
  JarDependencyManagement,
  JarDependencyManagementSetup,
)
from pants.backend.jvm.targets.jar_library import JarLibrary
from pants.backend.jvm.tasks.classpath_products import ClasspathProducts
from pants.backend.jvm.tasks.ivy_resolve import IvyResolve
from pants.backend.jvm.tasks.ivy_task_mixin import IvyResolveFingerprintStrategy
from pants.base.build_environment import get_buildroot
from pants.base.exceptions import TaskError
from pants.goal.goal import Goal
from pants.goal.task_registrar import TaskRegistrar as task
from pants.invalidation.cache_manager import VersionedTargetSet
from pants.java.jar.exclude import Exclude
from pants.util.dirutil import safe_concurrent_creation, safe_mkdir
from pants.util.memo import memoized_property
from typing import List

from fsqio.pants.ivy.global_classpath_task_mixin import GlobalClasspathTaskMixin


JSON_INDENT = 4


class JarDependencyGlobalManagementSetup(GlobalClasspathTaskMixin, JarDependencyManagementSetup):

  # NOTE(mateo): The GlobalClasspathMixin finds 'jar_library' definitions and inserts them into the build graph
  # as JarLibrary targets. If a jar_library defines a `managed_dependencies` attribute, this ensure the corresponding
  # ManagedJarDependency target is validated by the conflict manager.
  def execute(self):
    self._resolve_default_target()

    all_targets = set(self.context.targets()) | self.bag_target_closure
    jar_libs = [t for t in all_targets if isinstance(t, JarLibrary)]
    targets = {g.managed_dependencies for g in jar_libs if g.managed_dependencies is not None}
    for library in jar_libs:
      if library.managed_dependencies:
        targets.add(library.managed_dependencies)
    self._compute_artifact_sets(targets)


class IvyResolveWithGlobalExcludesStep(IvyResolveStep):
  def __init__(self, *args, **kwargs):
    global_excludes = kwargs.get('global_excludes', None)
    del kwargs['global_excludes']
    super(IvyResolveWithGlobalExcludesStep, self).__init__(*args, **kwargs)
    self.global_excludes = set(global_excludes)

  def _prepare_ivy_xml(self, targets, ivyxml, hash_name):
    jars, global_excludes = IvyUtils.calculate_classpath(targets)
    # Don't pass global excludes to ivy when using soft excludes.
    if self.soft_excludes:
      global_excludes = []
    IvyUtils.generate_ivy(targets, jars, global_excludes | self.global_excludes, ivyxml, self.confs,
                          hash_name, self.pinned_artifacts)


class IvyGlobalResolve(GlobalClasspathTaskMixin, IvyResolve):
  @classmethod
  def register_options(cls, register):
    super(IvyGlobalResolve, cls).register_options(register)
    register(
      '--global-excludes',
      type=list,
      member_type=dict,
      fingerprint=True,
      help='Global exclusions from the classpath.',
    )
    register(
      '--resolution-report-outdir',
      type=str,
      help='Output directory for Ivy transitive resolution mappings. The reports map JarLibrary to resolved coordinates'
      'for each ManagedDependencies target, as well as the transitive set of resolved coordinates.'
     )
    # This option is a temporary convienance while we audit if we want the coverage long term.
    register(
      '--disable-reports',
      type=bool,
      default=True,
      help='Skip writing the reports, even when a reporting dir is set.'
     )
    register(
      '--fail-on-diff',
      type=bool,
      default=False,
      help='When True, changes in 3rdparty jar dependencies raise an Exception instead of writing the report. '
      'This option is primarily useful in CI when requiring the generated report to be checked-in.'
    )

  @classmethod
  def alternate_target_roots(cls, options, address_mapper, build_graph):
    pass

  @memoized_property
  def global_excludes(self):
    return [Exclude(e['org'], e.get('name', None)) for e in self.get_options().global_excludes]

  @memoized_property
  def resolution_report_outdir(self):
    if self.get_options().resolution_report_outdir:
      return os.path.join(get_buildroot(), self.get_options().resolution_report_outdir)
    return None

  # NOTE(mateo): This is a very light fork where only change is `targets` by also including our bag target.
  #
  # The practical meaning is that instead of just grabbing everything in context, the resolve now includes
  # all nodes in the graph, whether they are connected to the target_roots context or not.
  def execute(self):
    executor = self.create_java_executor()
    targets = sorted(set(self.context.targets()) | self.bag_target_closure)
    compile_classpath = self.context.products.get_data(
      'compile_classpath',
      init_func=ClasspathProducts.init_func(self.get_options().pants_workdir)
    )
    results = self.resolve(
      executor=executor,
      targets=targets,
      classpath_products=compile_classpath,
      confs=self.get_options().confs,
      extra_args=self._args,
    )

    if self._report:

      results_with_resolved_artifacts = filter(lambda r: r.has_resolved_artifacts, results)

      if not results_with_resolved_artifacts:
        self.context.log.info("Not generating a report. No resolution performed.")
      else:
        for result in results_with_resolved_artifacts:
          self._generate_ivy_report(result)

  # HACK(tdyas):
  # The _ivy_resolve method below was copied (mostly) verbatim from IvyTaskMixin so that we
  # can modify it to support global excludes. The creation of IvyFetchStep and IvyResolveStep have
  # been refactored into the _create_ivy_fetch_step and _create_ivy_resolve_step methods. In the
  # _create_ivy_resolve_step method, a subclass of IvyResolveStep is made that supports global
  # excludes.
  #
  # NOTE(mateo): I later hacked in the 'resolution_report' option, in case we wanted to keep the
  # global_classpath option alive.
  #
  def _create_ivy_fetch_step(self,
                             confs,
                             resolve_hash_name,
                             pinned_artifacts,
                             soft_excludes,
                             ivy_cache_dir,
                             global_ivy_workdir):
    return IvyFetchStep(confs,
                        resolve_hash_name,
                        pinned_artifacts,
                        soft_excludes,
                        ivy_cache_dir,
                        global_ivy_workdir)

  def _create_ivy_resolve_step(self,
                               confs,
                               resolve_hash_name,
                               pinned_artifacts,
                               soft_excludes,
                               ivy_cache_dir,
                               global_ivy_workdir,
                               global_excludes,):
    return IvyResolveWithGlobalExcludesStep(confs,
                                            resolve_hash_name,
                                            pinned_artifacts,
                                            soft_excludes,
                                            ivy_cache_dir,
                                            global_ivy_workdir,
                                            global_excludes=global_excludes)

  def _ivy_resolve(self,
                   targets,
                   executor=None,
                   silent=False,
                   workunit_name=None,
                   confs=None,
                   extra_args=None,
                   invalidate_dependents=False,
                   pinned_artifacts=None):
    """Resolves external dependencies for the given targets."""
    # If there are no targets, we don't need to do a resolve.
    if not targets:
      return NO_RESOLVE_RUN_RESULT
    confs = confs or ('default',)
    fingerprint_strategy = IvyResolveFingerprintStrategy(confs)
    with self.invalidated(targets,
                          invalidate_dependents=invalidate_dependents,
                          silent=silent,
                          fingerprint_strategy=fingerprint_strategy) as invalidation_check:
      # In case all the targets were filtered out because they didn't participate in fingerprinting.
      if not invalidation_check.all_vts:
        return NO_RESOLVE_RUN_RESULT
      resolve_vts = VersionedTargetSet.from_versioned_targets(invalidation_check.all_vts)
      resolve_hash_name = resolve_vts.cache_key.hash
      global_ivy_workdir = os.path.join(self.context.options.for_global_scope().pants_workdir,
                                        'ivy')
      fetch = self._create_ivy_fetch_step(confs,
                                          resolve_hash_name,
                                          pinned_artifacts,
                                          self.get_options().soft_excludes,
                                          self.ivy_cache_dir,
                                          global_ivy_workdir)

      resolve = self._create_ivy_resolve_step(confs,
                                              resolve_hash_name,
                                              pinned_artifacts,
                                              self.get_options().soft_excludes,
                                              self.ivy_cache_dir,
                                              global_ivy_workdir,
                                              self.global_excludes)
      result = self._perform_resolution(
        fetch, resolve, executor, extra_args, invalidation_check, resolve_vts, resolve_vts.targets, workunit_name,
      )

      # NOTE(mateo): Wiring up our own reports, the full ivy report is too heavy weight for our purposes.
      if result.resolved_artifact_paths and self.resolution_report_outdir and not self.get_options().disable_reports:
        # This is added to get a reasonable handle for managed_dependencies target sets.
        # If there is more than one VT it defaults to the VTS.id, which is a non-human-readable cache key.
        # If we wanted to be more performant than rigorous, we could bail after the first query.
        managed_dependencies = set(
          j.target.managed_dependencies
          for j in invalidation_check.all_vts
          if isinstance(j.target, JarLibrary) and
          j.target.managed_dependencies is not None
        )

        if managed_dependencies and len(managed_dependencies) > 1:
          raise TaskError(
            'Each partition should be mapped to a single managed_dependencies target: (was: {})\n Targets: {}'
            .format(managed_dependencies, resolve_vts.targets)
          )
        default_target_name = JarDependencyManagement.global_instance()._default_target.name
        partition_name = list(managed_dependencies)[0].name if managed_dependencies else default_target_name
        self.write_resolve_report(resolve.frozen_resolve_file, partition_name)
      return result

  def write_resolve_report(self, frozen_resolve_file, partition_name):
    safe_mkdir(self.resolution_report_outdir)
    out_file = os.path.join(self.resolution_report_outdir, partition_name + '.json')

    def lines_from_json(json_str):
      # type: (str) -> List[str]
      return [s.strip() for s in json_str.splitlines()]

    def _diff(json1, json2, fromfile='commited', tofile='generated'):
      # type: (str, str, str, str) -> List[str]
      return list(unified_diff(lines_from_json(json1), lines_from_json(json2), fromfile=fromfile, tofile=tofile))

    try:
      with open(frozen_resolve_file) as fp:
        # We are alphabetizing the 3rdparty names and their resolved coordinateds to get a stable diff in the SCM.
        parsed = json.load(fp, object_pairs_hook=OrderedDict)

        for target, coords in parsed['default']['target_to_coords'].items():
          parsed['default']['target_to_coords'][target] = sorted(coords)

        parsed = OrderedDict(sorted(
          (key, val) for key, val in parsed['default']['target_to_coords'].items() if not key.startswith('//')
        ))

        # By default `json.dumps` uses the seperators ', ' and ': '. While that second one is fine, the first
        # one when used in conjunction with indent produces trailing whitespaces. Because many devs have IDEs
        # that go ahead and get rid of trailing whitespace this will create giant unwanted diffs. And because
        # it's generaly frowned upon, we override that setting so at to produce a trailing whitespace free json.
        # -- Mathieu
        new_report = json.dumps(parsed, indent=JSON_INDENT, separators=(',', ': '))

        if self.get_options().fail_on_diff:
          with open(out_file, 'r') as old_report_fd:
            old_report = old_report_fd.read()

          diff = _diff(old_report, new_report)
          if diff:
            pretty_diff = "\n".join(diff)
            raise TaskError(
              '\n{pretty_diff}\n\n'
              'Committed dependency file and resolved dependencies are different, '
              'please make sure you comitted latest dependency file (@ {path}). '
              'Check 3rdparty/reports/jvm/README.md for more help.'.format(
                pretty_diff=pretty_diff,
                path=out_file,
              )
            )

        with safe_concurrent_creation(out_file) as tmp_filename:
          with open(tmp_filename, 'wb') as f:
            f.write(new_report)
    except IOError as e:
      raise TaskError('Failed to dump resolution report to {}: {}'.format(out_file, e))


def register_goals():
  Goal.by_name('bootstrap').uninstall_task('jar-dependency-management')
  task(name='global-jar-dependency-management', action=JarDependencyGlobalManagementSetup).install('bootstrap')
  Goal.by_name('resolve').uninstall_task('ivy')
  task(name='ivy', action=IvyGlobalResolve).install('resolve', first=True)

  # Goal.by_name('pyprep').uninstall_task('requirements')
  # task(name='requirements', action=GlobalResolvePythonRequirements).install('pyprep')
