# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'twitter',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:mongodb-drivers',
    '3rdparty:twitter-util',
    'src/jvm/io/fsq/rogue/adapter/callback',
  ],
  provides=scala_artifact(
    org='io.fsq',
    name='rogue-adapter-callback-twitter',
    repo=oss_sonatype_repo,
  ),
)
