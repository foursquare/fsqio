# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'model',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:bson',
    '3rdparty:joda-time',
    '3rdparty:lift-json',
    '3rdparty:lift-mongo',
    '3rdparty:lift-record',
    '3rdparty:thrift',
    'src/jvm/io/fsq/exceptionator/model/io',
    'src/jvm/io/fsq/exceptionator/util',
    'src/jvm/io/fsq/rogue/lift',
    'src/jvm/io/fsq/spindle/common/thrift/json',
    'src/thrift/io/fsq/exceptionator/model',
  ],
  provides=scala_artifact(
    org='io.fsq',
    name='exceptionator-model',
    repo=oss_sonatype_repo,
    publication_metadata=io_fsq_library("""
      Foursquare's exception aggregator built on top of twitter finagle and mongodb.
    """),
  ),
  tags = [
    'opensource',
    'dependencies_must_have:opensource',
  ],
)
