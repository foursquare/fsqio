# Awkward dependency here, but it's our own scala records we care about serializing..
scala_record_library(
  name = 'test',
  sources = globs('*.thrift'),
  dependencies = [
    'src/jvm/io/fsq/spindle/runtime',
  ],
)
