# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'filter',
  sources = globs('*.scala'),
  tags = [
    'fscommon'
  ],
  dependencies = [
    '3rdparty:bson',
    '3rdparty:finagle',
    '3rdparty:twitter-util',
    '3rdparty:typesafe-config',
    'src/jvm/io/fsq/exceptionator/model/io',
    'src/jvm/io/fsq/exceptionator/util',
  ],
  provides=scala_artifact(
    org='io.fsq',
    name='exceptionator-filter',
    repo=oss_sonatype_repo,
  ),
)
