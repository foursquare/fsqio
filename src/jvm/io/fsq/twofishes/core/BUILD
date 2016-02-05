# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'core',
  sources = globs('*.scala', '*.java'),
  dependencies = [
    '3rdparty:commons-logging',  # java doesn't buildgen
    '3rdparty:hadoop-common',
    '3rdparty:hbase',  # java doesn't buildgen
    '3rdparty:jts',
    '3rdparty:mongodb',
    '3rdparty:thrift',
    'src/jvm/io/fsq/common/scala',
    'src/jvm/io/fsq/twofishes/util',
    'src/thrift/io/fsq/twofishes',
  ],
)
