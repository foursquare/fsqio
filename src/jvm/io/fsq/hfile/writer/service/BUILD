scala_library(
  name = 'service',
  dependencies = [
    '3rdparty:hadoop-common',
    'src/jvm/io/fsq/common/scala',
  ],
  sources = globs('*.scala'),
  provides=scala_artifact(
    org='io.fsq',
    name='hfile-writer-service',
    repo=oss_sonatype_repo,
  ),
)
