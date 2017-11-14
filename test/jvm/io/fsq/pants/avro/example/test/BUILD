# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

junit_tests(
  name = 'test',
  sources = globs('*.scala'),
  dependencies = [
    '//:scala-library',  # force scala-libarydep since target doesn't rely on any other scala library
    '3rdparty:junit',
    'src/avro/io/fsq/pants/avro/example',
  ],
)
