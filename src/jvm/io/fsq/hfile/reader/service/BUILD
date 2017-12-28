scala_library(
  name = 'service',
  sources = globs('*.scala'),
  provides=scala_artifact(
    org='io.fsq',
    name='hfile-reader-service',
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
