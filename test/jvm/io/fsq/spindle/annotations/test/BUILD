# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

junit_tests(
  name = 'test',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:junit',
    'src/jvm/io/fsq/spindle/annotations',
    'src/thrift/io/fsq/common/thrift/descriptors/types/gen',  # comment to fool buildgen
  ],
  tags = [
    'spindle_runtime',
  ],
)
