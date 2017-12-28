# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'util',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:bson',
    '3rdparty:cascading',
    '3rdparty:gt-geojson',
    '3rdparty:gt-shapefile',
    '3rdparty:hadoop-common',
    '3rdparty:hadoop-mapreduce-client',
    '3rdparty:jts',
    '3rdparty:scala-reflect',
    '3rdparty:scalding',
    '3rdparty:slf4j-all',
    '3rdparty:thrift',
    'src/jvm/io/fsq/common/scala',
    'src/jvm/io/fsq/spindle/runtime',
    'src/jvm/io/fsq/twofishes/core',
    'src/jvm/io/fsq/twofishes/util',
    'src/thrift/io/fsq/twofishes',
    'src/thrift/io/fsq/twofishes/model',
  ],
  provides=scala_artifact(
    org='io.fsq',
    name='twofishes-indexer-util',
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
