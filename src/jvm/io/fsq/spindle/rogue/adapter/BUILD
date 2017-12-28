# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'adapter',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:bson',
    '3rdparty:mongodb-drivers',
    'src/jvm/io/fsq/common/scala',
    'src/jvm/io/fsq/rogue',
    'src/jvm/io/fsq/rogue/adapter',
    'src/jvm/io/fsq/rogue/connection',
    'src/jvm/io/fsq/rogue/index',
    'src/jvm/io/fsq/spindle/rogue',
    'src/jvm/io/fsq/spindle/runtime',
  ],
  provides=scala_artifact(
    org='io.fsq',
    name='spindle-rogue-adapter',
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
