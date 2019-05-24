# Copyright 2019 Foursquare Labs Inc. All Rights Reserved.

scalac_plugin(
  name = 'emit-exported-symbols',
  plugin = 'buildgen-emit-exported-symbols',
  classname = 'io.fsq.buildgen.plugin.exported.EmitExportedSymbolsPlugin',
  provides=scala_artifact(
    org='io.fsq',
    name='buildgen-emit-exported-symbols',
    repo=oss_sonatype_repo,
      publication_metadata=io_fsq_library("""
        Scala compiler plugin that emits a text file of the symbols exported.
    """)
  ),
  sources = [
    'EmitExportedSymbolsPlugin.scala',
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
    '//:scalac',   # Possibly safe to remove, not sure if needed at runtime. Build and test.
  ],
)

jvm_binary(
  name = 'emit-exported-symbols-binary',
  tags = [
    'opensource',
    'buildgen',
  ],
  dependencies = [
    ':emit-exported-symbols',
    '3rdparty:scalac',
    '3rdparty:scala-reflect',
    '//:scalac',    # Possibly safe to remove, not sure if needed at runtime. Build and test.
  ],
)
