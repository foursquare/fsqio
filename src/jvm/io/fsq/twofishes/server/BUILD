# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

# TODO(dan): The hbase dep is is only there while we transition twofishes to our hfile reader.
scala_library(
  name = 'server',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:country-revgeo',
    '3rdparty:finagle',
    '3rdparty:gt-geojson',
    '3rdparty:hadoop-common',
    '3rdparty:hbase',
    '3rdparty:jackson',
    '3rdparty:jts',
    '3rdparty:mongodb',
    '3rdparty:netty',
    '3rdparty:ostrich',
    '3rdparty:s2',
    '3rdparty:scopt',
    '3rdparty:slf4s',
    '3rdparty:thrift',
    '3rdparty:twitter-util',
    'src/jvm/io/fsq/common/scala',
    'src/jvm/io/fsq/spindle/common/thrift/json',
    'src/jvm/io/fsq/spindle/runtime',
    'src/jvm/io/fsq/twofishes/core',
    'src/jvm/io/fsq/twofishes/country',
    'src/jvm/io/fsq/twofishes/hacks',
    'src/jvm/io/fsq/twofishes/server/resources/twofishes-static',
    'src/jvm/io/fsq/twofishes/server/resources/warmup',
    'src/jvm/io/fsq/twofishes/util',
    'src/thrift/io/fsq/twofishes',
  ],
)

jvm_binary(
  name = 'server-bin',
  main = 'io.fsq.twofishes.server.GeocodeFinagleServer',
  dependencies = [
    ':server',
  ]
)

jvm_binary(
  name = 'build-hotfix-file',
  main = 'io.fsq.twofishes.server.JsonHotfixFileBuilder',
  dependencies = [
    ':server',
  ]
)
