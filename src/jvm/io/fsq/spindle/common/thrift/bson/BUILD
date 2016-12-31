# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'bson',
  sources = globs('*.java','*.scala'),
  dependencies = [
    '3rdparty:mongodb-driver',  # we need the org.bson objects here.
    '3rdparty:thrift',
    'src/jvm/io/fsq/spindle/common/thrift/base',
    'src/jvm/io/fsq/spindle/common/thrift/json',
  ],
)
