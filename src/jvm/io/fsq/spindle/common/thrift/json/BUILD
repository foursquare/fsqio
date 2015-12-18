# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

java_library(
  name = 'json',
  sources = globs('*.java'),
  dependencies = [
    '3rdparty:fscommon-thrift-base',    # Force the jar dep as we deprecate com.foursquare.spindle for io.fsq.
    '3rdparty:fscommon-thrift-bson',    # Force the jar dep as we deprecate com.foursquare.spindle for io.fsq.
    '3rdparty:fscommon-thrift-json',    # Force the jar dep as we deprecate com.foursquare.spindle for io.fsq.
    '3rdparty:thrift',
    '3rdparty:jackson',
    'src/jvm/io/fsq/spindle/common/thrift/base',
  ],
)
