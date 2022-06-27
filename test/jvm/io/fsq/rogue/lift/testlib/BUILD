# Copyright 2022 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'testlib',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:bson',
    '3rdparty:junit',
    '3rdparty:lift-mongo',
    '3rdparty:lift-record',
    '3rdparty:lift-util',
    '3rdparty:mongodb-drivers',
    'src/jvm/io/fsq/common/scala',
    'src/jvm/io/fsq/rogue',
    'src/jvm/io/fsq/rogue/adapter',
    'src/jvm/io/fsq/rogue/adapter/lift',
    'src/jvm/io/fsq/rogue/connection',
    'src/jvm/io/fsq/rogue/index',
    'src/jvm/io/fsq/rogue/lift',
    'src/jvm/io/fsq/rogue/query',
    'src/jvm/io/fsq/rogue/query/lift',
    'src/jvm/io/fsq/rogue/util',
    'test/jvm/io/fsq/rogue/connection/testlib',
  ],
)
