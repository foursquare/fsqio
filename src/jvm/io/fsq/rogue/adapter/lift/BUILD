# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'lift',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:bson',
    '3rdparty:lift-mongo',
    '3rdparty:mongodb-drivers',
    'src/jvm/io/fsq/rogue',
    'src/jvm/io/fsq/rogue/adapter',
    'src/jvm/io/fsq/rogue/connection',
    'src/jvm/io/fsq/rogue/index',
    'src/jvm/io/fsq/rogue/shard',
  ],
  provides=scala_artifact(
    org='io.fsq',
    name='rogue-adapter-lift',
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
