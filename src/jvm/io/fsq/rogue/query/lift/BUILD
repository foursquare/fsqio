# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'lift',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:lift-mongo',
    '3rdparty:mongodb-drivers',
    'src/jvm/io/fsq/rogue',
    'src/jvm/io/fsq/rogue/lift',
    'src/jvm/io/fsq/rogue/query',
  ],
   provides=scala_artifact(
    org='io.fsq',
    name='rogue-query-lift',
    repo=oss_sonatype_repo,
  ),
)
