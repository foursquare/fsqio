# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'query',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:mongodb-drivers',
    'src/jvm/io/fsq/field',
    'src/jvm/io/fsq/rogue',
    'src/jvm/io/fsq/rogue/adapter',
    'src/jvm/io/fsq/rogue/index',
    'src/jvm/io/fsq/rogue/types',
  ],
  provides=scala_artifact(
    org='io.fsq',
    name='rogue-query',
    repo=oss_sonatype_repo,
  ),
)
