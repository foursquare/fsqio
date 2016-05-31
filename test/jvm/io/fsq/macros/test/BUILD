# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

junit_tests(
  name = 'test',
  sources = globs('*.scala'),
  tags = [
    'fscommon'
  ],
  dependencies = [
    '3rdparty:junit',
    '3rdparty:scala-library-2.10.4',  # override buildgen to include missing scala-library due to pants bug
    'src/jvm/io/fsq/macros',
  ],
)
