# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'rogue',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:bson',
    '3rdparty:mongodb-drivers',
    '3rdparty:thrift',
    'src/jvm/io/fsq/field',
    'src/jvm/io/fsq/rogue',
    'src/jvm/io/fsq/spindle/common/thrift/bson',
    'src/jvm/io/fsq/spindle/rogue/query',
    'src/jvm/io/fsq/spindle/runtime',
  ],
  provides=scala_artifact(
    org='io.fsq',
    name='spindle-rogue',
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
