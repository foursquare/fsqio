# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

scala_record_library(
  name = 'model',
  dependencies = [
    '3rdparty:bson',
    'src/jvm/io/fsq/spindle/runtime',
    'src/thrift/io/fsq/twofishes',
  ],
  sources = globs('*.thrift'),
  provides=scala_artifact(
    org='io.fsq',
    name='twofishes-index-models',
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
