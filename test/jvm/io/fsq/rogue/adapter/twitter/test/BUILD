# Copyright 2018 Foursquare Labs Inc. All Rights Reserved.

junit_tests(
  name = 'test',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:hamcrest-core',
    '3rdparty:junit',
    '3rdparty:mongodb-drivers',
    '3rdparty:twitter-util',
    'src/jvm/io/fsq/rogue',
    'src/jvm/io/fsq/rogue/adapter/twitter',
    'test/jvm/io/fsq/common/testing/matchers',
  ],
)
