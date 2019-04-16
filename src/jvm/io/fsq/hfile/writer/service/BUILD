scala_library(
  name = 'service',
  dependencies = [
    '3rdparty:hadoop-common',
  ],
  sources = globs('*.scala'),
  provides=scala_artifact(
    org='io.fsq',
    name='hfile-writer-service',
    repo=oss_sonatype_repo,
    publication_metadata=io_fsq_library("""
      Fsq.io common Hfile libraries, from Foursquare.
    """),
  ),
  tags = [
    'opensource',
    'dependencies_must_have:opensource',
  ],
)
