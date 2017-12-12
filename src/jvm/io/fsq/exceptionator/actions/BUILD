# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'actions',
  sources = globs('*.scala'),
  tags = [
    'fscommon'
  ],
  dependencies = [
    '3rdparty:bson',
    '3rdparty:joda-time',
    '3rdparty:twitter-util',
    'src/jvm/io/fsq/exceptionator/filter',
    'src/jvm/io/fsq/exceptionator/model',
    'src/jvm/io/fsq/exceptionator/model/io',
  ],
  provides=scala_artifact(
    org='io.fsq',
    name='exceptionator-actions',
    repo=oss_sonatype_repo,
  ),
)
