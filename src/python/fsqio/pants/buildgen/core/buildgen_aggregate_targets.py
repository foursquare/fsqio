# coding=utf-8
# Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

from __future__ import (
  absolute_import,
  division,
  generators,
  nested_scopes,
  print_function,
  unicode_literals,
  with_statement,
)

from pants.base.exceptions import TaskError
from pants.base.specs import DescendantAddresses
from pants.build_graph.address import Address

from fsqio.pants.buildgen.core.buildgen_target_bag import BuildgenTargetBag
from fsqio.pants.buildgen.core.buildgen_task import BuildgenTask


class BuildgenAggregateTargets(BuildgenTask):
  """Creates target bags that contain an aggregate of all matching target_types found under a specific source tree."""

  def __init__(self, context, workdir):
    super(BuildgenAggregateTargets, self).__init__(context, workdir)
    self._target_alias_whitelist = {'buildgen_target_bag'}

  def generate_target_type(self, build_graph, target_alias, source_tree, target_bag, additional_generated_targets=None,
                           ignored_targets_regex=None):
    extra_targets = additional_generated_targets or []
    generated_deps = {Address.parse(t) for t in extra_targets}

    for address in self.context.address_mapper.scan_specs([DescendantAddresses(source_tree)]):
      if not ignored_targets_regex or not ignored_targets_regex.match(address.spec):
        build_graph.inject_address_closure(address)
        target = build_graph.get_target(address)
        if address.spec_path != source_tree and target.type_alias == target_alias:
          generated_deps.add(address)

    self.adjust_target_build_file(
      build_graph.get_target(Address.parse(target_bag)),
      generated_deps,
      whitelist=self._target_alias_whitelist,
    )

  def execute(self):
    build_graph = self.context.build_graph
    target_bags = []
    # For each BuildgenTargetBag, gather the matching targets and aggregate them as dependencies of the bag target.
    for spec in self.buildgen_subsystem.buildgen_target_bags:
      address = Address.parse(spec)
      build_graph.inject_address_closure(address)
      bag_target = build_graph.get_target(address)
      if not isinstance(bag_target, BuildgenTargetBag):
        raise TaskError("The generated_target_bags option must point to a list of BuildgenTargetBag specs.")
      target_bags.append(bag_target)

    for target in target_bags:
      self.generate_target_type(
        build_graph,
        target_alias=target.target_type_alias,
        source_tree=target.source_tree,
        additional_generated_targets=target.additional_generated_targets,
        target_bag=target.address.spec,
        ignored_targets_regex=target.ignored_targets_regex,
      )
