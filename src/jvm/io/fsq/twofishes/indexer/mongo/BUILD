# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'mongo',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:bson',
    '3rdparty:mongodb-drivers',
    'src/jvm/io/fsq/common/scala',
    'src/jvm/io/fsq/rogue',
    'src/jvm/io/fsq/rogue/adapter',
    'src/jvm/io/fsq/rogue/connection',
    'src/jvm/io/fsq/rogue/index',
    'src/jvm/io/fsq/rogue/query',
    'src/jvm/io/fsq/rogue/util',
    'src/jvm/io/fsq/spindle/rogue',
    'src/jvm/io/fsq/spindle/rogue/adapter',
    'src/jvm/io/fsq/spindle/rogue/query',
    'src/jvm/io/fsq/spindle/runtime',
    'src/jvm/io/fsq/twofishes/indexer/util',
    'src/jvm/io/fsq/twofishes/util',
    'src/thrift/io/fsq/twofishes',
    'src/thrift/io/fsq/twofishes/model',
  ],
  provides=scala_artifact(
    org='io.fsq',
    name='twofishes-indexer-mongo',
    repo=oss_sonatype_repo,
    publication_metadata=io_fsq_library("""
      Foursquare's coarse, splitting geocoder and reverse geocoder in Scala.
    """),
  ),
  tags = [
    'opensource',
    'dependencies_must_have:opensource',
  ],
)
