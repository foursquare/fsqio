# coding=utf-8
# Copyright 2021 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function, unicode_literals

from pants.subsystem.subsystem import Subsystem


class PublishSubsystem(Subsystem):
  """Base class for subsytems that configure buildgen publishing .

  Subclasses can be further subclassed, manually, e.g., to add any extra options.
  """
  # Note: Probably should be broken into Publication and JvmPublication, which much of this
  # is more properly scoped to.
  options_scope = 'buildgen.publish'

  @classmethod
  def subsystem_dependencies(cls):
    return super(PublishSubsystem, cls).subsystem_dependencies()

  @classmethod
  def register_options(cls, register):
      # Flip for trouble and git reset BUILD files to good SHA. Run buildgen to update deps.
    register(
      "--skip",
      # Toggle?
      default=False,
      advanced=True,
      type=bool,
      help="Provides config  will not be generated or updated."
    )
    register(
      "--exportable-target-aliases",
      default=[
        'scala_library',
        'java_library',
      ],
      advanced=True,
      type=list,
      help="All transtive dependencies of the exportable library must be of these types.",
    )
    register(
      "--unexportable-target-aliases",
      default=[
        'junit_tests',
      ],
      advanced=True,
      type=list,
      help="Any transitive dependency on these target aliases blocks publish configuration."
    )
    register(
      "--opt-out-paths",
      default=[],
      advanced=True,
      type=list,
      help="No publishing configuration will be generated or updated under these directories."
    )
    # Repositories must be previously configured in a register.py. See existing publish
    # documentation on adding or updating repos.
    register(
      "--repo-map",
      default={},
      advanced=True,
      type=dict,
      help="Provides configuration under this path will be configured to use this repository."
    )
    # Metadata must be previously configured in a register.py. See existing publish
    # documentation on adding or updating metadata.
    register(
      "--publication-metadata-map",
      default={},
      advanced=True,
      type=dict,
      help="Provides configuration under this path will be configured with this metadata."
    )
