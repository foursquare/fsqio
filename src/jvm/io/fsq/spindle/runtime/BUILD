# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'runtime',
  sources = globs('*.scala'),
  tags = ['fscommon', 'spindle_runtime'],
  dependencies = [
    '3rdparty:joda-time',
    '3rdparty:thrift',
    'src/jvm/io/fsq/field',
    'src/jvm/io/fsq/spindle/common/thrift/bson',
    'src/jvm/io/fsq/spindle/common/thrift/json',
  ],
  provides=scala_artifact(
    org='io.fsq',
    name='spindle-runtime',
    repo=oss_sonatype_repo,
  ),
)
