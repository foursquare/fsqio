# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'enhanced',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:s2',
    'src/jvm/io/fsq/rogue',
  ],
  tags = [
    'opensource',
    'dependencies_must_have:opensource',
  ],
  provides = scala_artifact(org='io.fsq.spindle', name='enhanced', repo=oss_sonatype_repo),
)
