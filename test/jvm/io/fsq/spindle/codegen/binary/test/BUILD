# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

junit_tests(
  name = 'test',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:junit',
    'src/jvm/io/fsq/spindle/codegen:binary',
    'src/resources/io/fsq/ssp/codegen/scala:files',
    'test/jvm/io/fsq/spindle/codegen/binary/test/gen:files',
    'test/thrift/io/fsq/spindle/codegen/binary/test/gen',
    'test/thrift/io/fsq/spindle/codegen/parser/test:files',
  ],
  tags=[
    'spindle_codegen',
   ],
)
