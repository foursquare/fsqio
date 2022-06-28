# Copyright 2022 Foursquare Labs Inc. All Rights Reserved.

junit_tests(
  name = 'test',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:bson',
    '3rdparty:junit',
    '3rdparty:mockito',
    '3rdparty:mongodb-drivers',
    'src/jvm/io/fsq/rogue/connection',
    'src/jvm/io/fsq/spindle/rogue/adapter',
    'test/thrift/io/fsq/spindle/rogue/testlib',
  ],
)
