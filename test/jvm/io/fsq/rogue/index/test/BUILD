# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

junit_tests(
  name = 'test',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:junit',
    '3rdparty:lift-mongo',
    '3rdparty:specs2',
    'src/jvm/io/fsq/rogue/index',
    'test/jvm/io/fsq/rogue/test',
    'test/thrift/io/fsq/rogue/spindle/test',
  ],
)
