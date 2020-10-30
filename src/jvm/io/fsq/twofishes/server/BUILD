# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

# TODO(dan): The hbase dep is is only there while we transition twofishes to our hfile reader.
scala_library(
  name = 'server',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:bson',
    '3rdparty:finagle',
    '3rdparty:gt-geojson',
    '3rdparty:hadoop-common',
    '3rdparty:hbase',
    '3rdparty:jackson',
    '3rdparty:jts',
    '3rdparty:netty',
    '3rdparty:s2',
    '3rdparty:scopt',
    '3rdparty:slf4s',
    '3rdparty:thrift',
    '3rdparty:twitter-util',
    'src/jvm/io/fsq/common/scala',
    'src/jvm/io/fsq/geo/quadtree',
    'src/jvm/io/fsq/net/stats',
    'src/jvm/io/fsq/spindle/common/thrift/json',
    'src/jvm/io/fsq/spindle/runtime',
    'src/jvm/io/fsq/twitter/ostrich/stats',
    'src/jvm/io/fsq/twofishes/core',
    'src/jvm/io/fsq/twofishes/country',
    'src/jvm/io/fsq/twofishes/hacks',
    'src/jvm/io/fsq/twofishes/server/resources/twofishes-static',
    'src/jvm/io/fsq/twofishes/server/resources/warmup',
    'src/jvm/io/fsq/twofishes/util',
    'src/thrift/io/fsq/twofishes',
  ],
  provides=scala_artifact(
    org='io.fsq',
    name='twofishes-server',
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

jvm_binary(
  name = 'server-bin',
  main = 'io.fsq.twofishes.server.GeocodeFinagleServer',
  dependencies = [
    ':server',
  ],
  tags = [
    'opensource',
    'dependencies_must_have:opensource',
  ],
)

jvm_binary(
  name = 'build-hotfix-file',
  main = 'io.fsq.twofishes.server.JsonHotfixFileBuilder',
  dependencies = [
    ':server',
  ],
  tags = [
    'opensource',
    'dependencies_must_have:opensource',
  ],
)
