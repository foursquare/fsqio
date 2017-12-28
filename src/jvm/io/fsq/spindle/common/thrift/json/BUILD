# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

java_library(
  name = 'json',
  sources = globs('*.java'),
  dependencies = [
    '3rdparty:thrift',
    '3rdparty:jackson',
    'src/jvm/io/fsq/spindle/common/thrift/base',
  ],
  tags = [
    'opensource',
    'dependencies_must_have:opensource',
  ],
  provides=artifact(
    org='io.fsq',
    name='spindle-common-thrift-json',
    repo=oss_sonatype_repo,
    publication_metadata=io_fsq_library("""
      Foursquare's Scala code generator for Thrift.
    """),
  ),
)
