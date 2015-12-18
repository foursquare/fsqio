# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

junit_tests(
  name = 'test',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:junit',
    '3rdparty:mongodb',
    '3rdparty:thrift',
    'src/jvm/io/fsq/spindle/common/thrift/bson',
    'src/jvm/io/fsq/spindle/runtime',
    'test/thrift/io/fsq/spindle/codegen/test',
  ],
)
