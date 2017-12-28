# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'fhttp',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:commons-httpclient',
    '3rdparty:commons-logging',  # Runtime dep.
    '3rdparty:finagle',
    '3rdparty:netty',
    '3rdparty:scala-xml',
    '3rdparty:twitter-util',
    'src/jvm/io/fsq/common/scala',
  ],
  provides=scala_artifact(
    org='io.fsq',
    name='fhttp',
    repo=oss_sonatype_repo,
    publication_metadata=io_fsq_library("""
      Foursquare's scala-idiomatic request building interface similar for finagle clients.
    """),
  ),
  tags = [
    'opensource',
    'dependencies_must_have:opensource',
  ],
)
