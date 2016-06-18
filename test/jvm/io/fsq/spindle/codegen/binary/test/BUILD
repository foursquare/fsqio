# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

junit_tests(
  name = 'test',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:junit',
    'src/jvm/io/fsq/spindle/codegen/binary',
    'src/jvm/io/fsq/spindle/codegen/runtime',
    'src/thrift/io/fsq/common/thrift/descriptors/programs/gen',  # runtime dependency.
  ],
)
