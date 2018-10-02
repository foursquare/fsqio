# Copyright 2018 Foursquare Labs Inc. All Rights Reserved.

# These are very large binary resources. They are hosted from the foursquare/cc-shapefiles
# Github repo in a tarball, and are downloaded + jarred-up at build time.
remote_source(
  name='cc-shapefiles.tar.gz',
  version='0.1b',
  tags = [
    'opensource',
    'dependencies_must_have:opensource',
  ],
  extract = True,
  platform_dependent = "False",
)

scala_library(
  name = 'quadtree',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:gt-main',
    '3rdparty:gt-shapefile',
    '3rdparty:jts',
    ':cc-shapefiles.tar.gz',
  ],
  provides=scala_artifact(
    org='io.fsq',
    name='geo.quadtree',
    repo=oss_sonatype_repo,
    publication_metadata=io_fsq_library("""
      Shapefile library and resources for reverse geocoder.
    """),
  ),
  tags = [
    'opensource',
    'dependencies_must_have:opensource',
  ],
)


jvm_binary(
  name = 'simplifier-binary',
  main = 'io.fsq.geo.quadtree.ShapefileSimplifier',
  dependencies = [
    ':quadtree',
  ],
  tags = [
    'opensource',
    'dependencies_must_have:opensource',
  ],
)
