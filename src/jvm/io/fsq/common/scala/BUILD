# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'scala',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:bson',
  ],
  tags = [
    'spindle_codegen',
  ],
  provides = scala_artifact(
    org = 'io.fsq',
    name = 'common-scala',
    repo = oss_sonatype_repo,
  ),
)
