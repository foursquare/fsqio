# coding=utf-8
# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

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
from itertools import chain
import os
import re

from pants.backend.jvm.targets.scala_library import ScalaLibrary
from pants.backend.jvm.tasks.nailgun_task import NailgunTask
from pants.base.build_environment import get_buildroot
from pants.base.exceptions import TaskError
from pants.build_graph.address import Address
from pants.option.custom_types import target_option
from pants.util.memo import memoized_property

from fsqio.pants.spindle.targets.spindle_thrift_library import SpindleThriftLibrary
from fsqio.pants.spindle.targets.ssp_template import SspTemplate
from fsqio.pants.spindle.tasks.spindle_task import SpindleTask
from fsqio.pants.util.dirutil import safe_mkdir


class SpindleGen(NailgunTask, SpindleTask):
  """Generate codegen for spindle libraries."""

  @classmethod
  def product_types(cls):
    return ['scala']

  @classmethod
  def register_options(cls, register):
    super(SpindleGen, cls).register_options(register)
    register(
      '--jvm-options',
      default=[],
      advanced=True,
      type=list,
      help='Use these jvm options when running Spindle.',
    )
    register(
      '--thrift-include',
      advanced=True,
      type=list,
      help='Use these thrift files as spindle bases for codegen.',
    )
    register(
      '--runtime-dependency',
      advanced=True,
      type=list,
      help='A list of targets that all spindle codegen depends on at runtime.',
    )
    register(
      '--scala-ssp-template',
      fingerprint=True,
      advanced=True,
      type=target_option,
      help='Use this target as the scala templates for spindle codegen (required to be 1 target).',
    )
    register(
      '--java-ssp-template',
      fingerprint=True,
      advanced=True,
      type=target_option,
      help='Use this target as the java templates for spindle codegen (required to be 1 target).',
    )

  @memoized_property
  def java_template(self):
    return self.get_spindle_target('java_ssp_template', self.get_options().java_ssp_template, SspTemplate)

  @memoized_property
  def scala_template(self):
    return self.get_spindle_target('scala_ssp_template', self.get_options().scala_ssp_template, SspTemplate)

  @classmethod
  def prepare(cls, options, round_manager):
    super(SpindleGen, cls).prepare(options, round_manager)
    round_manager.require('spindle_binary')

  @property
  def synthetic_target_extra_dependencies(self):
    return set(
      dep_target
      for dep_spec in self.get_options().runtime_dependency
      for dep_target in self.context.resolve(dep_spec)
    )

  @property
  def namespace_out(self):
    return os.path.join(self.workdir, 'scala_record')

  def build_spindle_product(self):
    spindle_products = self.context.products.get('spindle_binary')
    products = spindle_products.get(self.spindle_target)
    if products:
      for directory, product in products.items():
        for prod in product:
          # In this case we know there is just one product in the spindle_binary product.
          yield os.path.join(directory, prod)

  def codegen_targets(self):
    return self.context.targets(lambda t: isinstance(t, SpindleThriftLibrary))

  def sources_generated_by_target(self, target):
    return [
      os.path.join(self.namespace_out, relative_genned_source)
      for thrift_source in target.sources_relative_to_buildroot()
      for relative_genned_source in calculate_genfiles(thrift_source)
    ]

  def _run_spindle(self, args):
    try:
      spindle_binary = next(self.build_spindle_product())
      os.path.isfile(spindle_binary)
    except Exception as e:
      raise TaskError("Could not find the spindle binary at {}:\n{}".format(spindle_binary, e))

    # TODO Declare a task_subsystems dependency on JVM and use that to get options.
    java_main = 'io.fsq.spindle.codegen.binary.ThriftCodegen'
    return self.runjava(classpath=spindle_binary, jvm_options=self.get_options().jvm_options, main=java_main,
                        args=args, workunit_name='spindle-codegen')

  def _execute_codegen(self, targets):
    sources = self._calculate_sources(targets, lambda t: isinstance(t, SpindleThriftLibrary))
    scalate_workdir = os.path.join(self.workdir, 'scalate_workdir')
    safe_mkdir(self.namespace_out)
    # Spindle incorrectly caches state in its workdir so delete those files in sucess or failure.
    safe_mkdir(scalate_workdir, clean=True)

    thrift_include = self.get_options().thrift_include
    if not thrift_include:
      raise self.BadDependency("You must pass the paths of your thrift roots as the '--thrift_include' option!")

    scala_template_address = os.path.join(self.scala_template.address.spec_path,
                                          self.scala_template.entry_point)
    java_template_address = os.path.join(self.java_template.address.spec_path,
                                         self.java_template.entry_point)
    spindle_args = [
      '--template', scala_template_address,
      '--java_template', java_template_address,
      '--thrift_include', ':'.join(thrift_include),
      '--namespace_out', self.namespace_out,
      '--working_dir', scalate_workdir,
    ]

    spindle_args.extend(sources)
    result = self._run_spindle(spindle_args)
    if result != 0:
      raise TaskError('Spindle codegen exited non-zero ({0})'.format(result))

  def execute(self):
    targets = self.codegen_targets()
    build_graph = self.context.build_graph

    with self.invalidated(targets, invalidate_dependents=True) as invalidation_check:
      invalid_targets = list(chain.from_iterable(
        vts.targets for vts in invalidation_check.invalid_vts
      ))
      if invalid_targets:
        self._execute_codegen(invalid_targets)

      invalid_vts_by_target = dict([(vt.target, vt) for vt in invalidation_check.invalid_vts])
      vts_artifactfiles_pairs = defaultdict(list)

      for target in targets:
        synthetic_name = '{0}-{1}'.format(target.id, 'scala')
        spec_path = os.path.relpath(self.namespace_out, get_buildroot())
        synthetic_address = Address(spec_path, synthetic_name)
        generated_scala_sources = [
          '{0}.{1}'.format(source, 'scala')
          for source in self.sources_generated_by_target(target)
        ]
        generated_java_sources = [
          os.path.join(os.path.dirname(source), 'java_{0}.java'.format(os.path.basename(source)))
          for source in self.sources_generated_by_target(target)
        ]
        relative_generated_sources = [
          os.path.relpath(src, self.namespace_out)
          for src in generated_scala_sources + generated_java_sources
        ]
        synthetic_target = self.context.add_new_target(
          address=synthetic_address,
          target_type=ScalaLibrary,
          dependencies=self.synthetic_target_extra_dependencies,
          sources=relative_generated_sources,
          derived_from=target,
        )

        # NOTE: This bypasses the convenience function (Target.inject_dependency) in order
        # to improve performance.  Note that we can walk the transitive dependee subgraph once
        # for transitive invalidation rather than walking a smaller subgraph for every single
        # dependency injected.  This walk also covers the invalidation for the java synthetic
        # target above.
        for dependent_address in build_graph.dependents_of(target.address):
          build_graph.inject_dependency(dependent=dependent_address,
                                        dependency=synthetic_target.address)
        # NOTE: See the above comment.  The same note applies.
        for concrete_dependency_address in build_graph.dependencies_of(target.address):
          build_graph.inject_dependency(
            dependent=synthetic_target.address,
            dependency=concrete_dependency_address,
          )
        build_graph.walk_transitive_dependee_graph(
          [target.address],
          work=lambda t: t.mark_transitive_invalidation_hash_dirty(),
        )

        if target in self.context.target_roots:
          self.context.target_roots.append(synthetic_target)
        if target in invalid_vts_by_target:
          vts_artifactfiles_pairs[invalid_vts_by_target[target]].extend(
            generated_scala_sources + generated_java_sources
          )

      if self.artifact_cache_writes_enabled():
        self.update_artifact_cache(vts_artifactfiles_pairs.items())

  def _calculate_sources(self, thrift_targets, target_filter):
    sources = set()
    def collect_sources(target):
      if target_filter(target):
        sources.update(target.sources_relative_to_buildroot())
    for target in thrift_targets:
      target.walk(collect_sources)
    return sources


# Slightly hacky way to figure out which files get generated from a particular thrift source.
# TODO: This could be emitted by the codegen tool.
# That would also allow us to easily support 1:many codegen.
NAMESPACE_PARSER = re.compile(r'^\s*namespace\s+([^\s]+)\s+([^\s]+)\s*$')

def calculate_genfiles(source):
  abs_source = os.path.join(get_buildroot(), source)
  with open(abs_source, 'r') as thrift:
    lines = thrift.readlines()
  namespaces = {}
  for line in lines:
    match = NAMESPACE_PARSER.match(line)
    if match:
      lang = match.group(1)
      namespace = match.group(2)
      namespaces[lang] = namespace

  namespace = namespaces.get('java')

  if not namespace:
    raise TaskError('No namespace provided in source: {}'.format(abs_source))

  return calculate_scala_record_genfiles(namespace, abs_source)


def calculate_scala_record_genfiles(namespace, source):
  """Returns the generated file basenames, add .java or .scala to get the full path."""
  basepath = namespace.replace('.', '/')
  name = os.path.splitext(os.path.basename(source))[0]
  return [os.path.join(basepath, name)]
