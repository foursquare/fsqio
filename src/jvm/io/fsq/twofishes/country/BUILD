# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'country',
  sources = globs('*.scala'),
  dependencies = [
    'src/jvm/io/fsq/common/scala',
    'src/jvm/io/fsq/twofishes/country/data',
  ],
  provides=scala_artifact(
    org='io.fsq',
    name='twofishes-countryinfo',
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
