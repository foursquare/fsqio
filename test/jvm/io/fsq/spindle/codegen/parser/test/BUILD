# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

junit_tests(
  name = 'test',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:junit',
    'src/jvm/io/fsq/spindle/codegen:binary',
    'test/thrift/io/fsq/spindle/codegen/parser/test:files',
  ],
  tags = [
    'spindle_codegen',
  ],
)
