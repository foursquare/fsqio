# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

# NOTE(mateo): This doesn't actually directly use the spindle.codegen backend, it just verifies
# that the current protocols defined in the spindle.runtime.TProtocol are properly serialized
# post-codegen.
junit_tests(
  name = 'test',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:bson',
    '3rdparty:junit',
    '3rdparty:thrift',
    'src/jvm/io/fsq/spindle/codegen:binary',
    'src/jvm/io/fsq/spindle/runtime',
    'test/jvm/io/fsq/testlib',
    'test/thrift/io/fsq/spindle/codegen/runtime/constants/test/gen',
    'test/thrift/io/fsq/spindle/codegen/runtime/enhanced_types/test/gen',
  ],
)
