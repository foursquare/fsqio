# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

junit_tests(
  name = 'test',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:bson',
    '3rdparty:junit',
    '3rdparty:lift-util',
    '3rdparty:mongodb-drivers',
    'src/jvm/io/fsq/field',
    'src/jvm/io/fsq/rogue',
    'src/jvm/io/fsq/rogue/adapter',
    'src/jvm/io/fsq/rogue/adapter/callback/twitter',
    'src/jvm/io/fsq/rogue/query',
    'test/jvm/io/fsq/rogue/connection/testlib',
    'test/jvm/io/fsq/rogue/query/testlib',
  ],
)
