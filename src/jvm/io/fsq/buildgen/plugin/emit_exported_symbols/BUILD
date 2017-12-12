
scalac_plugin(
  name = 'emit-exported-symbols',
  plugin = 'buildgen-emit-exported-symbols',
  classname = 'io.fsq.buildgen.plugin.emit_exported_symbols.EmitExportedSymbolsPlugin',
  provides=scala_artifact(
    org='io.fsq',
    name='buildgen-emit-exported-symbols',
    repo=oss_sonatype_repo,
      publication_metadata=io_fsq_library("""
        Scala compiler plugin that emits a text file of the symbols exported.
    """)
  ),
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:scalac',
    '3rdparty:scala-reflect',
  ],
)

jvm_binary(
  name = 'emit-exported-symbols-binary',
  tags = [
    'opensource',
    'fscommon',
  ],
  dependencies = [
    ':emit-exported-symbols',
    '3rdparty:scalac',
    '3rdparty:scala-reflect',
  ],
)
