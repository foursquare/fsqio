# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'service',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:finagle',
    '3rdparty:joda-time',
    '3rdparty:json4s',
    '3rdparty:ostrich',
    '3rdparty:twitter-util',
    'src/jvm/io/fsq/common/logging',
    'src/jvm/io/fsq/exceptionator/actions',
    'src/jvm/io/fsq/exceptionator/actions/concrete',
    'src/jvm/io/fsq/exceptionator/filter',
    'src/jvm/io/fsq/exceptionator/loader/concrete',
    'src/jvm/io/fsq/exceptionator/loader/service',
    'src/jvm/io/fsq/exceptionator/model/io',
    'src/jvm/io/fsq/exceptionator/mongo',
    'src/jvm/io/fsq/exceptionator/mongo/concrete',
    'src/jvm/io/fsq/exceptionator/util',
    'src/resources/io/fsq/exceptionator/css',
    'src/resources/io/fsq/exceptionator/html',
    'src/resources/io/fsq/exceptionator/js',
  ],
  provides=scala_artifact(
    org='io.fsq',
    name='exceptionator',
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
