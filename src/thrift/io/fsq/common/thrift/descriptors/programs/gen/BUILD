
scala_record_library(
  name = 'gen',
  sources = globs('*.thrift'),
  tags = [
    'fscommon',
  ],
  dependencies = [
    'src/jvm/io/fsq/spindle/runtime',
    'src/thrift/io/fsq/common/thrift/descriptors/constants/gen',
    'src/thrift/io/fsq/common/thrift/descriptors/enums/gen',
    'src/thrift/io/fsq/common/thrift/descriptors/headers/gen',
    'src/thrift/io/fsq/common/thrift/descriptors/services/gen',
    'src/thrift/io/fsq/common/thrift/descriptors/structs/gen',
    'src/thrift/io/fsq/common/thrift/descriptors/types/gen',
  ],
)
