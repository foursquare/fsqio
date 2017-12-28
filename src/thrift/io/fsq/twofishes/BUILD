# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

scala_record_library(
  name = 'twofishes',
  dependencies = [
    '3rdparty:bson',
    '3rdparty:twitter-util',
    'src/jvm/io/fsq/spindle/runtime',
  ],
  sources = globs('*.thrift'),
  provides=scala_artifact(
    org='io.fsq',
    name='twofishes-records',
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
