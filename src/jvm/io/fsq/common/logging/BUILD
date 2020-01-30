# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'logging',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:twitter-util',
  ],
  provides = scala_artifact(
    org = 'io.fsq',
    name = 'common-logging',
    repo = oss_sonatype_repo,
    publication_metadata=io_fsq_library("""
      Fsq.io common libraries, from Foursquare.
    """),
  ),
  tags = [],
)

