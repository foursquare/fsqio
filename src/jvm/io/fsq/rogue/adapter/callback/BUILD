# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'callback',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:mongodb-drivers',
    'src/jvm/io/fsq/common/scala',
  ],
   provides=scala_artifact(
    org='io.fsq',
    name='rogue-adapter-callback',
    repo=oss_sonatype_repo,
  ),
)
