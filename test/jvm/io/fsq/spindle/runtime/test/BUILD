# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

junit_tests(
  name = 'test',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:jackson',
    '3rdparty:junit',
    '3rdparty:lift-json',
    '3rdparty:mongodb',
    '3rdparty:phonenumbers',
    '3rdparty:specs',
    '3rdparty:thrift',
    'src/jvm/io/fsq/spindle/common/thrift/base',
    'src/jvm/io/fsq/spindle/common/thrift/bson',
    'src/jvm/io/fsq/spindle/common/thrift/json',
    'src/jvm/io/fsq/spindle/runtime',
    'test/thrift/io/fsq/spindle/codegen/test',
    'test/thrift/io/fsq/spindle/thriftexample',
    'test/thrift/io/fsq/spindle/thriftexample/av',
    'test/thrift/io/fsq/spindle/thriftexample/people',
    'test/thrift/io/fsq/spindle/thriftexample/talent',
  ],
)
