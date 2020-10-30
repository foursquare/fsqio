scala_library(
  name = 'mmap',
  dependencies = [
    '3rdparty:hadoop-common',
    '3rdparty:snappy-java',
    '3rdparty:twitter-util',
    'src/jvm/io/fsq/common/base',
    'src/jvm/io/fsq/common/logging',
    'src/jvm/io/fsq/common/scala',
    'src/jvm/io/fsq/hfile/reader/service',
    'src/jvm/io/fsq/twitter/ostrich/stats',
  ],
  sources = globs('*.scala'),
  provides=scala_artifact(
    org='io.fsq',
    name='hfile-reader-concrete-mmap',
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
