# Copyright 2022 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'shard',
  sources = globs('*.scala'),
  provides=scala_artifact(
    org='io.fsq',
    name='rogue-shard',
    repo=oss_sonatype_repo,
    publication_metadata=io_fsq_library("""
      Foursquare's Scala DSL for constructing and executing find and modify commands against
      MongoDB
    """),
  ),
  tags = [
    'opensource',
    'rogue',
    'dependencies_must_have:rogue',
    'dependencies_must_have:opensource',
  ],
)
