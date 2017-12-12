
scalac_plugin(
  name = 'emit_used_symbols',
  plugin = 'buildgen-emit_used_symbols',
  classname = 'io.fsq.buildgen.plugin.emit_used_symbols.EmitUsedSymbolsPlugin',
  provides=scala_artifact(
    org='io.fsq',
    name='buildgen-emit-used-symbols',
    repo=oss_sonatype_repo,
      publication_metadata=io_fsq_library("""
        Scala compiler plugin that emits a text file of the symbols used.
    """)
  ),
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:scalac',
    '3rdparty:scala-reflect',
  ],
)

jvm_binary(
  name = 'emit-used-symbols-binary',
  tags = [
    'opensource',
    'fscommon',
  ],
  dependencies = [
    ':emit_used_symbols',
    '3rdparty:scalac',
    '3rdparty:scala-reflect',
  ],
)
