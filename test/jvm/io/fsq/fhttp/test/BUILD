# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

junit_tests(
  name = 'test',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:finagle',
    '3rdparty:junit',
    '3rdparty:netty4',  # runtime
    '3rdparty:twitter-util',
    'src/jvm/io/fsq/common/scala',
    'src/jvm/io/fsq/fhttp',
  ],
  concurrency="SERIAL",
)
