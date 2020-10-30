# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'util',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:bson',
    '3rdparty:caliper',
    '3rdparty:gt-shapefile',
    '3rdparty:jts',
    '3rdparty:s2',
    '3rdparty:scala-reflect',
    '3rdparty:slf4s',
    '3rdparty:twitter-util',
    'src/jvm/io/fsq/common/scala',
    'src/jvm/io/fsq/twitter/ostrich/stats',
    'src/jvm/io/fsq/twofishes/util/data',
    'src/thrift/io/fsq/twofishes',
  ],
  provides=scala_artifact(
    org='io.fsq',
    name='twofishes-util',
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
