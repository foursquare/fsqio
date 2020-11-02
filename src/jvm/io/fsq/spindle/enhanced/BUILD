# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'enhanced',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:s2',
    'src/jvm/io/fsq/rogue',
  ],
  tags = [
    'opensource',
    'dependencies_must_have:opensource',
  ],
  provides = scala_artifact(
    org = 'io.fsq',
    name = 'spindle-enhanced',
    repo=oss_sonatype_repo,
    publication_metadata=io_fsq_library("""
      Foursquare's Scala code generator for Thrift.
    """),
  ),
)
