# Copyright 2020 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'lift',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:lift-json',
  ],
  provides = scala_artifact(
    org = 'io.fsq',
    name = 'json-lift',
    repo=oss_sonatype_repo,
    publication_metadata=io_fsq_library("""
      Fsq.io common libraries for using JSON with Lift framework, from Foursquare.
    """),
  ),
)
