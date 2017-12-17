# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'io',
  sources = globs('*.scala'),
  tags = [
    'fscommon'
  ],
  dependencies = [
    '3rdparty:lift-json',
    'src/jvm/io/fsq/exceptionator/util',
  ],
  provides=scala_artifact(
    org='io.fsq',
    name='exceptionator-model-io',
    repo=oss_sonatype_repo,
    publication_metadata=io_fsq_library("""
      Foursquare's exception aggregator built on top of twitter finagle and mongodb.
    """),
  ),
)
