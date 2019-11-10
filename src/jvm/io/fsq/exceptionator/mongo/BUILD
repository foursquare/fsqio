# Copyright 2018 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'mongo',
  sources = globs('*.scala'),
  tags = [
    'fscommon',
    'opensource',
    'dependencies_must_have:opensource',
  ],
  dependencies = [
    '3rdparty:mongodb-drivers',
    'src/jvm/io/fsq/rogue/adapter',
    'src/jvm/io/fsq/rogue/connection',
    'src/jvm/io/fsq/rogue/query',
    'src/jvm/io/fsq/spindle/rogue/adapter',
    'src/jvm/io/fsq/spindle/runtime',
  ],
  provides=scala_artifact(
    org='io.fsq',
    name='exceptionator-mongo',
    repo=oss_sonatype_repo,
    publication_metadata=io_fsq_library("""
      Foursquare's exception aggregator built on top of twitter finagle and mongodb.
    """),
  ),
)
