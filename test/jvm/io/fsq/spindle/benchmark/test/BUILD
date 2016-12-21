
java_library(
  name = 'test',
  sources = globs('*.java'),
  dependencies = [
    '3rdparty:joda-time',
    '3rdparty:jmh',
    
    '3rdparty:thrift',
    'test/thrift/io/fsq/spindle/benchmark',
    'test/thrift/io/fsq/spindle/runtime/structs/gen',
  ],
)

jvm_binary(
  name = 'jmh',
  main = 'org.openjdk.jmh.Main',
  dependencies = [
    '3rdparty:jmh',
    ':test',
  ],
)
