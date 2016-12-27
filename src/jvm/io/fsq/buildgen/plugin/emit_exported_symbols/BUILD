
scalac_plugin(
  name = 'emit_exported_symbols',
  plugin = 'emit-exported-symbols',
  classname = 'io.fsq.buildgen.plugin.emit_exported_symbols.EmitExportedSymbolsPlugin',
  provides = artifact(
    org = 'io.fsq',
    name = 'buildgen.plugin.emit_exported_symbols',
    repo = oss_sonatype_repo,
  ),
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:scalac',
    '3rdparty:scala-reflect',
  ],
)

jvm_binary(
  name = 'emit_exported_symbols-binary',
  tags = [
    'opensource',
    'fscommon',
  ],
  dependencies = [
    ':emit_exported_symbols',
    '3rdparty:scalac',
    '3rdparty:scala-reflect',
  ],
)
