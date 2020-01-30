# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

# TODO(mateo): Define some useful metadata.
scala_library(
  name = 'algorithm',
  sources = globs('*.scala'),
  dependencies = [
    'src/jvm/io/fsq/common/scala',
  ],
  tags = [],
  provides = scala_artifact(
    org = 'io.fsq',
    name = 'common-algorithm',
    repo = oss_sonatype_repo,
    publication_metadata=io_fsq_library("""
      Fsq.io common libraries, from Foursquare.
    """),
  ),
)

