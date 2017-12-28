# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'serde',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:thrift',
  ],
  provides=scala_artifact(
    org='io.fsq',
    name='spindle-common-thrift-serde',
    repo=oss_sonatype_repo,
    publication_metadata=io_fsq_library("""
      Foursquare's Scala code generator for Thrift.
    """),
  ),
  tags = [
    'opensource',
    'dependencies_must_have:opensource',
  ],
)
