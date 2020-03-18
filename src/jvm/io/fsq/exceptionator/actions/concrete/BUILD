# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'concrete',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:bson',
    '3rdparty:finagle',
    '3rdparty:joda-time',
    '3rdparty:lift-json',
    '3rdparty:mongodb-drivers',
    '3rdparty:twitter-util',
    'src/jvm/com/twitter/ostrich/stats',
    'src/jvm/io/fsq/common/logging',
    'src/jvm/io/fsq/common/scala',
    'src/jvm/io/fsq/exceptionator/actions',
    'src/jvm/io/fsq/exceptionator/filter',
    'src/jvm/io/fsq/exceptionator/filter/concrete',
    'src/jvm/io/fsq/exceptionator/loader/service',
    'src/jvm/io/fsq/exceptionator/model',
    'src/jvm/io/fsq/exceptionator/model/io',
    'src/jvm/io/fsq/exceptionator/mongo',
    'src/jvm/io/fsq/exceptionator/util',
    'src/jvm/io/fsq/spindle/rogue',
    'src/thrift/io/fsq/exceptionator/model',
  ],
  tags = [
    'opensource',
    'dependencies_must_have:opensource',
  ],
  provides=scala_artifact(
    org='io.fsq',
    name='exceptionator-actions-concrete',
    repo=oss_sonatype_repo,
    publication_metadata=io_fsq_library("""
      Foursquare's exception aggregator built on top of twitter finagle and mongodb.
    """),
  ),
)
