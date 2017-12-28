# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

# NOTE(mateo): This target uses rglobs to make explicit that the subfolders are not available
# for consumption by any outside targets. Nothing should depend on this library or any
# code underneath this BUILD file.
#
# If you are looking to consume the thrift descriptors, you should consider that choice very
# carefully, and then look at the scala_record_library defined in src/thrift/io/fsq/common/thrift.

scala_library(
  name = 'binary',
  sources = rglobs('*.scala', '*.java'),
  dependencies = [
    '3rdparty:commons-io',
    '3rdparty:jackson-scala',
    '3rdparty:joda-time',
    '3rdparty:parboiled-scala',
    '3rdparty:scalate',
    '3rdparty:scopt',
    '3rdparty:thrift',
    'src/jvm/io/fsq/common/scala',
    'src/jvm/io/fsq/field',
    'src/jvm/io/fsq/spindle/common/thrift/bson',
    'src/jvm/io/fsq/spindle/common/thrift/json',
  ],
  tags = [
    'opensource',
    'dependencies_must_have:opensource',
    'dependencies_cannot_have:spindle_runtime',
    'dependees_must_have:spindle_codegen',
  ],
  provides = scala_artifact(
    org = 'io.fsq',
    name = 'spindle-codegen',
    repo = oss_sonatype_repo,
    publication_metadata=io_fsq_library("""
      Foursquare's Scala code generator for Thrift.
    """),
  ),
)

jvm_binary(
  name = 'spindle',
  main = 'io.fsq.spindle.codegen.binary.ThriftCodegen',
  dependencies = [
    ':binary',
  ],
  tags = [
    'spindle_codegen',
  ],
)
