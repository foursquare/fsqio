# coding=utf-8
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

from __future__ import (
  absolute_import,
  division,
  generators,
  nested_scopes,
  print_function,
  unicode_literals,
  with_statement,
)

from pants.subsystem.subsystem import Subsystem


class BuildgenSubsystem(object):
  """Subsystem for Buildgen global configuration."""

  class Factory(Subsystem):
    options_scope = 'buildgen'

    @classmethod
    def register_options(cls, register):
      # TODO(mateo): Is there a way to surface the configured Source Roots here? Not sure if we have that sort
      # of guarantee about option loading - although you might think that source_roots would be known by this point.
      register(
        "--source-dirs",
        default=['src', '3rdparty'],
        advanced=True,
        type=list,
        help="Source directory that holds targets to buildgen - defaults to the configured source roots.",
      )
      register(
        '--test-dirs',
        default=['test'],
        advanced=True,
        type=list,
        help="Source directory that holds targets to buildgen - defaults to the configured source roots.",
      )
      register(
        '--target-whitelist',
        default=['target'],
        advanced=True,
        type=list,
        help='Full list of target callables that are allowed to be injected into the build file context.',
      )
      register(
        '--managed-dependency-aliases',
        advanced=True,
        type=list,
        help='The superset of target aliases that buildgen can inject as dependencies '
             '(e.g. [scala_library, python_library, ...].',
      )
      register(
        '--buildgen-target-bags',
        default=[],
        advanced=True,
        type=list,
        help="Each listed BuildgenTargetBag will have all configured targets aggregated and injected as dependencies",
      )
      register(
        '--dry-run',
        default=False,
        type=bool,
        help='When True, buildgen will rewrite BUILD files in-place, otherwise it will print a diff to stdout.'
      )

    def create(self):
      options = self.get_options()
      return BuildgenSubsystem(
        options.source_dirs,
        options.test_dirs,
        options.target_whitelist,
        options.managed_dependency_aliases,
        options.buildgen_target_bags,
        options.dry_run
      )

  def __init__(self,
               source_dirs,
               test_dirs,
               target_whitelist,
               managed_dependency_aliases,
               buildgen_target_bags,
               dry_run):
    self.source_dirs = source_dirs
    self.test_dirs = test_dirs
    self.target_alias_whitelist = target_whitelist
    self.managed_dependency_aliases = managed_dependency_aliases
    self.buildgen_target_bags = buildgen_target_bags
    self.dry_run = dry_run
