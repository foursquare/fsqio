# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'concrete',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:finagle',
    '3rdparty:twitter-util',
    'src/jvm/io/fsq/exceptionator/filter',
    'src/jvm/io/fsq/exceptionator/model/io',
    'src/jvm/io/fsq/exceptionator/util',
  ],
  provides=scala_artifact(
    org='io.fsq',
    name='exceptionator-filter-concrete',
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
