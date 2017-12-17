# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'service',
  sources = globs('*.scala'),
  tags = [
    'fscommon'
  ],
  provides=scala_artifact(
    org='io.fsq',
    name='exceptionator-loader-service',
    repo=oss_sonatype_repo,
    publication_metadata=io_fsq_library("""
      Foursquare's exception aggregator built on top of twitter finagle and mongodb.
    """),
  ),
)
