# Copyright 2020 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'stats',
  dependencies = [
    '3rdparty:jackson-scala',
    '3rdparty:twitter-util',
  ],
  sources = globs('*.scala'),
  provides = scala_artifact(
    org='io.fsq',
    name='twitter.ostrich.stats',
    repo=oss_sonatype_repo,
    publication_metadata=io_fsq_library("""
      Light fork of com.twitter.ostrich Stats module, for use in Foursquare's Fsq.io.
    """),
  ),
)
