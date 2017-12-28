# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'replayer',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:commons-logging',  # Runtime dep.
    '3rdparty:finagle',
    '3rdparty:hadoop-common',
    '3rdparty:thrift',
    '3rdparty:twitter-util',
    'src/thrift/io/fsq/twofishes',
  ],
  provides=scala_artifact(
    org='io.fsq',
    name='twofishes-replayer',
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
