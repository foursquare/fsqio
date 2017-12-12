# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'concrete',
  sources = globs('*.scala'),
  tags = [
    'fscommon'
  ],
  dependencies = [
    'src/jvm/io/fsq/common/logging',
    'src/jvm/io/fsq/exceptionator/actions',
    'src/jvm/io/fsq/exceptionator/loader/service',
  ],
  provides=scala_artifact(
    org='io.fsq',
    name='exceptionator-loader-concrete',
    repo=oss_sonatype_repo,
  ),
)
