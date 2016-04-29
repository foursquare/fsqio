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

from pants.base.build_environment import get_buildroot
from pants.base.cmd_line_spec_parser import CmdLineSpecParser
from pants.base.specs import DescendantAddresses

from fsqio.pants.buildgen.core.buildgen_task import BuildgenTask


class Buildgen(BuildgenTask):

  @classmethod
  def alternate_target_roots(cls, options, address_mapper, build_graph):
    # NOTE(pl): This hack allows us to avoid sprinkling dummy tasks into every goal that we
    # need to be sure has its target roots modified.
    targets_required_downstream = set()

    buildgen_dirs = options.test_dirs + options.source_dirs
    source_dirs = [DescendantAddresses(d) for d in buildgen_dirs]

    for address in address_mapper.scan_specs(source_dirs):
      build_graph.inject_address_closure(address)
      targets_required_downstream.add(build_graph.get_target(address))
    return targets_required_downstream

  def execute(self):
    # NOTE(pl): We now rely on the fact that we've scheduled Buildgen (the dummy task in the
    # buildgen goal) to run before the real buildgen tasks, e.g. buildgen-scala, buildgen-thrift,
    # etc.  Since we are being run before the real tasks but after everything else upstream,
    # we can fix the target roots back up to be whatever the buildgen tasks are supposed to
    # operate on (instead of the entire build graph, which the upstream operated on).

    build_graph = self.context.build_graph
    bg_target_roots = set()

    # NOTE(mateo): Using all source roots adds a scan of 3rdparty - may have a minor perf penalty.
    # I would like to switch to the configured source roots if I can find a way to surface it everywhere.
    buildgen_dirs = self.buildgen_subsystem.source_dirs + self.buildgen_subsystem.test_dirs
    all_buildgen_specs = ['{}::'.format(d) for d in buildgen_dirs]

    spec_parser = CmdLineSpecParser(get_buildroot())
    target_specs = self.context.options._target_specs or all_buildgen_specs
    parsed_specs = [
      spec_parser.parse_spec(target_spec)
      for target_spec in target_specs
    ]
    for address in self.context.address_mapper.scan_specs(parsed_specs):
      build_graph.inject_address_closure(address)
      bg_target_roots.add(build_graph.get_target(address))
    # Disable building from SCM for now, and instead always build everything unless the user
    # specifies less.
    # bg_target_roots.update(
    #   target for target in
    #   build_graph.transitive_dependees_of_addresses(t.address for t in self.targets_from_scm())
    #   if target.is_original
    # )
    self.context._replace_targets(list(bg_target_roots))
