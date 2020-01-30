
scala_record_library(
  name = 'gen',
  sources = globs('*.thrift'),
  dependencies = [
    'src/jvm/io/fsq/spindle/runtime',
    'src/thrift/io/fsq/common/thrift/descriptors/headers/gen',
    'src/thrift/io/fsq/common/thrift/descriptors/structs/gen',
  ],
  tags = [],
)

# Required to bring the thrift files into an integration test's chroot.
files(
  name = 'files',
  sources = globs('*thrift'),
  scope="runtime",
)
