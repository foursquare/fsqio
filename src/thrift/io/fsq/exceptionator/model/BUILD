# Copyright 2018 Foursquare Labs Inc. All Rights Reserved.

scala_record_library(
  name = 'model',
  dependencies = [
    '3rdparty:bson',
    '3rdparty:joda-time',
    'src/jvm/io/fsq/spindle/runtime',
    'src/thrift/io/fsq/exceptionator',
  ],
  sources = globs('*.thrift'),
  tags = [
    'opensource',
    'dependencies_must_have:opensource',
  ],
)
