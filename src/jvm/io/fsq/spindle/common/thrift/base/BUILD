# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

java_library(
  name = 'base',
  sources = globs('*.java'),
  dependencies = [
    '3rdparty:thrift',
    '3rdparty:joda-time',
  ],
  tags = [
    'spindle_codegen',
  ],
  provides=scala_artifact(
    org='io.fsq',
    name='spindle-common-thrift-base',
    repo=oss_sonatype_repo,
    publication_metadata=io_fsq_library("""
      Foursquare's Scala code generator for Thrift.
    """),
  ),
)
