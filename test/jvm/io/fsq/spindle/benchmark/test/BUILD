
java_library(
  name = 'test',
  sources = globs('*.java'),
  dependencies = [
    '3rdparty:joda-time',
    '3rdparty:jmh',
    '3rdparty:thrift',
    'src/jvm/io/fsq/spindle/common/thrift/serde',
    'test/thrift/io/fsq/spindle/benchmark',
    'test/thrift/io/fsq/spindle/runtime/structs/gen',
  ],
  zinc_file_manager = False,
)

jvm_binary(
  name = 'jmh',
  main = 'org.openjdk.jmh.Main',
  dependencies = [
    '3rdparty:jmh',
    ':test',
  ],
)
