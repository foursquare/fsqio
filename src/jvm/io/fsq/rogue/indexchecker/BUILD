# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'indexchecker',
  sources = globs('*.scala'),
  dependencies = [
    'src/jvm/io/fsq/rogue',
    'src/jvm/io/fsq/rogue/index',
  ],
  provides=scala_artifact(
    org='io.fsq',
    name='rogue-indexchecker',
    repo=oss_sonatype_repo,
  ),
)
