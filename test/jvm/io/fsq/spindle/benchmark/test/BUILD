
java_library(
  name = 'test',
  sources = globs('*.java'),
  dependencies = [
    '3rdparty:jmh',
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
