# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

junit_tests(
  name = 'test',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:junit',
    'src/jvm/io/fsq/spindle/codegen:binary',
    'test/jvm/io/fsq/spindle/codegen/binary/test/gen:files',
    'test/thrift/io/fsq/spindle/codegen/parser/test',
  ],
  tags=[
    'spindle_codegen',
   ]
)
