# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'hacks',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:commons-logging',  # Runtime dep.
    '3rdparty:hadoop-common',
    '3rdparty:hbase',  # buildgen gets confused by the hacks
  ],
  provides=scala_artifact(
    org='io.fsq',
    name='twofishes-hacks',
    repo=oss_sonatype_repo,
    publication_metadata=io_fsq_library("""
      Foursquare's coarse, splitting geocoder and reverse geocoder in Scala.
    """),
  ),
  tags = [
    'opensource',
    'dependencies_must_have:opensource',
  ],
)
