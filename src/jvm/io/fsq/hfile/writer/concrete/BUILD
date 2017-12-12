scala_library(
  name = 'concrete',
  dependencies = [
    '3rdparty:guava',
    '3rdparty:hadoop-common',
    'src/jvm/io/fsq/common/scala',
    'src/jvm/io/fsq/hfile/common',
    'src/jvm/io/fsq/hfile/writer/service',
  ],
  sources = globs('*.scala'),
  provides=scala_artifact(
    org='io.fsq',
    name='hfile-writer-concrete',
    repo=oss_sonatype_repo,
  )
)

