# Copyright 2019 Foursquare Labs Inc. All Rights Reserved.

scalac_plugin(
  name = 'emit-used-symbols',
  plugin = 'buildgen-emit-used-symbols',
  classname = 'io.fsq.buildgen.plugin.used.EmitUsedSymbolsPlugin',
  provides=scala_artifact(
    org='io.fsq',
    name='buildgen-emit-used-symbols',
    repo=oss_sonatype_repo,
      publication_metadata=io_fsq_library("""
        Scala compiler plugin that emits a text file of the symbols used.
    """)
  ),
  sources = [
    'EmitUsedSymbolsPlugin.scala',
  ],
  tags = [
    'opensource',
    'buildgen',
    'dependencies_must_have:opensource',
    'dependencies_must_have:buildgen',
  ],
  dependencies = [
    '3rdparty:scalac',
    '3rdparty:scala-reflect',
    '//:scalac',    # Possibly safe to remove, not sure if needed at runtime. Build and test.
  ],
)

jvm_binary(
  name = 'emit-used-symbols-binary',
  tags = [
    'opensource',
    'buildgen',
  ],
  dependencies = [
    ':emit-used-symbols',
    '3rdparty:scalac',
    '3rdparty:scala-reflect',
    '//:scalac',    # Possibly safe to remove, not sure if needed at runtime. Build and test.
  ],
)
