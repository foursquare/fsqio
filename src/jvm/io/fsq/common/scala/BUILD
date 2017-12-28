# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'scala',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:bson',
  ],
  provides = scala_artifact(
    org = 'io.fsq',
    name = 'common-scala',
    repo=oss_sonatype_repo,
    publication_metadata=io_fsq_library("""
      Fsq.io common libraries, from Foursquare.
    """),
  ),
  tags = [
    'opensource',
    'fscommon',
    'spindle_codegen',
    'dependencies_must_have:opensource',
    'dependencies_must_have:fscommon',
  ],
)
