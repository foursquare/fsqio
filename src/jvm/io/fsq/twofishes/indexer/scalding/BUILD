# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'scalding',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:cascading',
    '3rdparty:commons-net',
    '3rdparty:gt-geojson',
    '3rdparty:hadoop-common',
    '3rdparty:icu4j',
    '3rdparty:json4s',
    '3rdparty:jts',
    '3rdparty:scalding',
    'src/jvm/io/fsq/common/scala',
    'src/jvm/io/fsq/geo/quadtree',
    'src/jvm/io/fsq/twofishes/indexer/importers/geonames',
    'src/jvm/io/fsq/twofishes/indexer/output',
    'src/jvm/io/fsq/twofishes/indexer/util',
    'src/jvm/io/fsq/twofishes/util',
    'src/thrift/io/fsq/twofishes',
  ],
  provides=scala_artifact(
    org='io.fsq',
    name='twofishes-indexer-scalding',
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
