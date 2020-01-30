# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'concurrent',
  dependencies = [
    '3rdparty:finagle',
    '3rdparty:twitter-util',
    'src/jvm/io/fsq/common/base',
    'src/jvm/io/fsq/common/scala',
    'src/jvm/io/fsq/macros',
  ],
  sources = globs('*.scala'),
  provides = scala_artifact(
    org = 'io.fsq',
    name = 'common-concurrent',
    repo = oss_sonatype_repo,
    publication_metadata=io_fsq_library("""
      Fsq.io common libraries, from Foursquare.
    """),
  ),
  tags = [],
)
