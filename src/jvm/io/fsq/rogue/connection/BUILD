# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'connection',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:bson',
    '3rdparty:mongodb-drivers',
    'src/jvm/io/fsq/common/scala',
  ],
  provides=scala_artifact(
    org='io.fsq',
    name='rogue-connection',
    repo=oss_sonatype_repo,
  ),
)
