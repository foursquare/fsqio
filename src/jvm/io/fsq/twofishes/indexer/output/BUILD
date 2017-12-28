# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'output',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:bson',
    '3rdparty:hadoop-common',
    '3rdparty:hbase',
    '3rdparty:jts',
    '3rdparty:mongodb-drivers',
    '3rdparty:thrift',
    '3rdparty:twitter-util',
    'src/jvm/io/fsq/common/scala',
    'src/jvm/io/fsq/rogue',
    'src/jvm/io/fsq/twofishes/core',
    'src/jvm/io/fsq/twofishes/hacks',
    'src/jvm/io/fsq/twofishes/indexer/mongo',
    'src/jvm/io/fsq/twofishes/indexer/util',
    'src/jvm/io/fsq/twofishes/util',
    'src/thrift/io/fsq/twofishes',
    'src/thrift/io/fsq/twofishes/model',
  ],
  provides=scala_artifact(
    org='io.fsq',
    name='twofishes-indexer-output',
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
