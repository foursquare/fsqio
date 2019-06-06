# coding=utf-8
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

"""
# TODO(mateo): Upstream Pants javadoc chokes on scala_libraries with mixed *.java and
# *.scala sources, because the javadoc task only checks for target.has_sources('*java').
# We hack the fix by also opting out if isinstance(target, ScalaLibrary) but a bug should be
# filed upstream.
"""

from __future__ import absolute_import, division, print_function

import os

from pants.backend.jvm.ossrh_publication_metadata import (
  Developer,
  License,
  OSSRHPublicationMetadata,
  Scm,
)
from pants.backend.jvm.repository import Repository
from pants.base.build_environment import get_buildroot
from pants.build_graph.build_file_aliases import BuildFileAliases


oss_sonatype_repo = Repository(
  name='oss_sonatype_repo',
  url='https://oss.sonatype.org/#stagingRepositories',
  push_db_basedir=os.path.join(get_buildroot(), 'build-support', 'fsqio', 'pushdb'),
)


def io_fsq_publication_metadata(description):
  return OSSRHPublicationMetadata(
    description=description,
    url='http://github.com/foursquare/fsqio',
    licenses=[
      License(
        name='Apache License, Version 2.0',
        url='http://www.apache.org/licenses/LICENSE-2.0'
      )
    ],
    developers=[
      Developer(
        name='Fsq.io, OSS projects from Foursquare.',
        url='https://github.com/foursquare/fsqio'
      )
    ],
    scm=Scm.github(
      user='foursquare',
      repo='fsqio'
    )
  )


def build_file_aliases():
  return BuildFileAliases(
    objects={
      'io_fsq_library': io_fsq_publication_metadata,
      'oss_sonatype_repo': oss_sonatype_repo,
    },
  )
