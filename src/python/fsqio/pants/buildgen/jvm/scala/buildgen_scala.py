# coding=utf-8
# Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function, unicode_literals

from pants.backend.jvm.subsystems.scala_platform import ScalaPlatform
from pants.base.exceptions import TaskError
from pants.util.memo import memoized_property

from fsqio.pants.buildgen.core.buildgen_task import BuildgenTask
from fsqio.pants.buildgen.core.subsystems.publish_subsystem import PublishSubsystem


class BuildgenScala(BuildgenTask):

  @classmethod
  def prepare(cls, options, round_manager):
    super(BuildgenScala, cls).prepare(options, round_manager)
    round_manager.require_data('java_source_to_exported_symbols')
    round_manager.require_data('scala')
    round_manager.require_data('scala_library_to_used_addresses')
    round_manager.require_data('scala_source_to_exported_symbols')
    round_manager.require_data('scala_source_to_used_symbols')

  @classmethod
  def register_options(cls, register):
    register(
      '--fatal',
      default=False,
      type=bool,
      help="When True, any imports that cannot be mapped raise and exception. When False, just print a warning."
    )

  @classmethod
  def subsystem_dependencies(cls):
    return super(BuildgenScala, cls).subsystem_dependencies() + (
      PublishSubsystem.scoped(cls), ScalaPlatform,
    )

  @classmethod
  def product_types(cls):
    return [
      'buildgen_scala',
    ]

  @memoized_property
  def _publish_subsystem(self):
    return PublishSubsystem.scoped_instance(self)

  @property
  def supported_target_aliases(self):
    return ('java_tests', 'junit_tests', 'scala_library')

  @property
  def _scala_library_to_used_addresses(self):
    return self.context.products.get_data('scala_library_to_used_addresses')

  # Pants extensively validates state at publish time. In the case of Jvm checking targets
  # are instances of ExportableJvmLibrary. We could use that method here and get away with less
  # target_aliases in the config but it would require fully hydrating the build graph, including
  # synthetic targets. ThriftLibrary is not exportable, the synthetic JavaLibrary from codegen is.
  # This also allows us to blocklist some targets that (suspiciously) pass ExportableJvmLibrary
  # instance checks, types we consider problematic to publish.
  @property
  def exportable_target_types(self):
    return [
      'java_library',
      'scala_library',
      'spindle_thrift_library',
      'scala_record_library',
    ]

  @memoized_property
  def skip_provides(self):
    return self._publish_subsystem.get_options().skip

  @memoized_property
  def opt_out_paths(self):
    return self._publish_subsystem.get_options().opt_out_paths

  @memoized_property
  def repo_map(self):
    return self._publish_subsystem.get_options().repo_map

  @memoized_property
  def publication_metadata_map(self):
    return self._publish_subsystem.get_options().publication_metadata_map

  @property
  def unexportable_target_types(self):
    return self._publish_subsystem.get_options().unexportable_target_aliases

  @property
  def artifact_type(self):
    return "scala_artifact"

  def target_name(self, target):
    return target.name

  # Arbitrary choice to have org name match the spec_path (i.e. everything between the source root
  # to thw target directory).
  # Previous pattern was to always use io.fsq, went this route for parsing simplicity.
  def target_org(self, target):
    _, org_path = target.address.spec_path.split("{}/".format(target.target_base))
    return org_path.replace('/', '.')

  def target_publication_metadata(self, target):
    metadata = None
    for directory_root in self.publication_metadata_map:
      if target.address.spec_path.startswith(directory_root):
        metadata = self.publication_metadata_map.get(directory_root)
    return metadata

  def target_repo(self, target):
    repo = None
    for directory_root in self.repo_map:
      if target.address.spec_path.startswith(directory_root):
        repo = self.repo_map.get(directory_root)
    return repo

  def unexportable(self, target):
    return (
      self.skip_provides
      or any(target.address.spec_path.startswith(t) for t in self.opt_out_paths)
      or any(t.type_alias in self.unexportable_target_types for t in target.closure())
    )

  def get_provides(self, target):
    # Check the various ways this target can opt-out of generating provides configuration.
    # This will not remove provides - that could be done but would have to happen within the manipulator.
    if self.unexportable(target):
      return None
    return self._construct_provides(target)

  def _construct_provides(self, target):
      org = self.target_org(target)
      name = self.target_name(target)
      repo = self.target_repo(target)
      metadata = self.target_publication_metadata(target)
      provides_lines = [
        "org='{}',".format(org),
        "name='{}',".format(name),
        "repo={},".format(repo),
        "publication_metadata={},".format(metadata),
      ]

      # Raise an error before generating an incomplete provides clause.
      if any(t is None for t in [org, name, repo, metadata]):
        raise TaskError("Target is misconfigured for buildgen provides: {}, {}".format(
          target, provides_lines
        ))
      return provides_lines

  def buildgen_target(self, scala_target):
    addresses_used_by_target = set(
      self.context.build_graph.get_target(addr).concrete_derived_from.address
      for addr in self._scala_library_to_used_addresses[scala_target]
    )
    filtered_addresses_used_by_target = set(
      addr for addr in addresses_used_by_target
      if addr != scala_target.address
    )
    self.adjust_target_build_file(scala_target, filtered_addresses_used_by_target)
