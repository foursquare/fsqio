# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'core',
  sources = globs('*.scala', '*.java'),
  dependencies = [
    '3rdparty:bson',
    '3rdparty:commons-logging',  # java doesn't buildgen
    '3rdparty:hadoop-common',
    '3rdparty:hbase',  # java doesn't buildgen
    '3rdparty:jts',
    '3rdparty:thrift',
    'src/jvm/io/fsq/twofishes/util',
    'src/thrift/io/fsq/twofishes',
  ],
  provides=scala_artifact(
    org='io.fsq',
    name='twofishes-core',
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
