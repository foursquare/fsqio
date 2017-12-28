# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'query',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:lift-mongo',
    '3rdparty:mongodb-drivers',
    'src/jvm/io/fsq/common/scala',
    'src/jvm/io/fsq/rogue',
    'src/jvm/io/fsq/rogue/query',
    'src/jvm/io/fsq/spindle/common/thrift/bson',
    'src/jvm/io/fsq/spindle/runtime',
  ],
  provides=scala_artifact(
    org='io.fsq',
    name='spindle-rogue-query',
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
