# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'macros',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:scala-reflect',
  ],
  provides = scala_artifact(
    org = 'io.fsq',
    name = 'macros',
    repo = oss_sonatype_repo,
    publication_metadata=io_fsq_library("""
      Fsq.io Scala Macros, from Foursquare.
    """),
  ),
  tags = [],
)
