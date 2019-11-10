# Copyright 2018 Foursquare Labs Inc. All Rights Reserved.

scala_record_library(
  name = 'model',
  dependencies = [
    '3rdparty:bson',
    '3rdparty:joda-time',
    'src/jvm/io/fsq/spindle/runtime',
    'src/thrift/io/fsq/exceptionator',
  ],
  provides=scala_artifact(
    org='io.fsq',
    name='exceptionator-model-records',
    repo=oss_sonatype_repo,
    publication_metadata=io_fsq_library("""
      Foursquare's exception aggregator built on top of twitter finagle and mongodb.
    """),
  ),
  sources = globs('*.thrift'),
  tags = [
    'opensource',
    'dependencies_must_have:opensource',
  ],
)
