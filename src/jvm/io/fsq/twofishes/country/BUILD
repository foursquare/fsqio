# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'country',
  sources = globs('*.scala'),
  dependencies = [
    'src/jvm/io/fsq/twofishes/country/data',
  ],
  provides=scala_artifact(
    org='io.fsq',
    name='twofishes-countryinfo',
    repo=oss_sonatype_repo,
  ),
)
