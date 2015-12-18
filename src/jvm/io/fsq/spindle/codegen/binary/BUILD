# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'binary',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:argot',
    '3rdparty:scalate',
    'src/jvm/io/fsq/spindle/__shaded_for_spindle_bootstrap__/runtime',
    'src/jvm/io/fsq/spindle/codegen/parser',
    'src/jvm/io/fsq/spindle/codegen/runtime',
  ],
  tags = ['dependencies_cannot_have:spindle_runtime'],
)


jvm_binary(
  name = 'spindle',
  main = 'io.fsq.spindle.codegen.binary.ThriftCodegen',
  dependencies = [
    ':binary',
  ],
)
