
scalac_plugin(
  name = 'emit_used_symbols',
  plugin = 'emit-used-symbols',
  classname = 'io.fsq.buildgen.plugin.emit_used_symbols.EmitUsedSymbolsPlugin',
  provides = artifact(
    org = 'io.fsq',
    name = 'buildgen.plugin.emit_used_symbols',
    repo = oss_sonatype_repo,
    ),
  sources = globs('*.scala')
)

jvm_binary(
  name = 'emit_used_symbols-binary',
  dependencies = [
    ':emit_used_symbols',
  ],
)
