# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'runtime',
  sources = globs('*.scala'),
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
    publication_metadata=io_fsq_library("""
      Foursquare's Scala code generator for Thrift.
    """),
  ),
  tags = [
    'opensource',
    'spindle_runtime',
    'dependencies_must_have:opensource',
  ],
)
