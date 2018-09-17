scala_record_library(
  name = 'exceptionator',
  dependencies = [
    '3rdparty:bson',
    '3rdparty:joda-time',
  ],
  sources = globs('*.thrift'),
  provides=scala_artifact(
    org='io.fsq',
    name='exceptionator-records',
    repo=oss_sonatype_repo,
    publication_metadata=io_fsq_library("""
      Foursquare's exception aggregator built on top of twitter finagle and mongodb.
    """),
  ),
  tags = [
    'opensource',
    'dependencies_must_have:opensource',
  ],
)
