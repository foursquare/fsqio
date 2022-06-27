# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

junit_tests(
  name = 'test',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:bson',
    '3rdparty:hamcrest-core',
    '3rdparty:junit',
    '3rdparty:mongodb-drivers',
    'src/jvm/io/fsq/field',
    'src/jvm/io/fsq/rogue',
    'src/jvm/io/fsq/spindle/common/thrift/bson',
    'src/jvm/io/fsq/spindle/rogue',
    'src/jvm/io/fsq/spindle/rogue/query',
    'src/jvm/io/fsq/spindle/runtime',
    'test/jvm/io/fsq/common/testing/matchers',
    'test/thrift/io/fsq/spindle/rogue/query/testlib',
  ],
)
