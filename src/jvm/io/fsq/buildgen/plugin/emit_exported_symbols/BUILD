
scalac_plugin(
  name = 'emit_exported_symbols',
  plugin = 'emit-exported-symbols',
  classname = 'io.fsq.buildgen.plugin.emit_exported_symbols.EmitExportedSymbolsPlugin',
  provides = artifact(
    org = 'io.fsq',
    name = 'buildgen.plugin.emit_exported_symbols',
    repo = oss_sonatype_repo,
  ),
  sources = globs('*.scala')
)

jvm_binary(
  name = 'emit_exported_symbols-binary',
  dependencies = [
    ':emit_exported_symbols',
  ],
)
