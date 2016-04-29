# coding=utf-8
# Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import

import json
import os
import re
import shutil
import tempfile

from pants.backend.jvm.subsystems.scala_platform import ScalaPlatform
from pants.backend.jvm.tasks.jvm_tool_task_mixin import JvmToolTaskMixin
from pants.base.build_environment import get_buildroot


class ScalacBuildgenTaskMixin(JvmToolTaskMixin):
  """A utility for invoking the scalac compiler with a custom plugin that short-circuits."""

  _SCALAC_MAIN = 'scala.tools.nsc.Main'

  @classmethod
  def register_scalac_buildgen_jvm_tools(cls, register):
    cls.register_jvm_tool(
      register,
      'used-symbols-bootstrap',
      classpath_spec='//:buildgen-emit-used-symbols',
    )
    cls.register_jvm_tool(
      register,
      'exported-symbols-bootstrap',
      classpath_spec='//:buildgen-emit-exported-symbols',
    )

  @property
  def _compiler_jars(self):
    return ScalaPlatform.global_instance().compiler_classpath(self.context.products)

  def _split_out_plugin_jar_from_classpath(self, classpath):
    plugin_jars = []
    other_jars = []
    for jar_path in classpath:
      if 'buildgen-emit' in jar_path:
        plugin_jars.append(jar_path)
      else:
        other_jars.append(jar_path)
    if not plugin_jars:
      raise Exception('Plugin jar not found in classpath: {0}'.format(classpath))
    elif len(plugin_jars) > 1:
      raise Exception('Multiple plugin jars found in classpath: {0}'.format(classpath))
    else:
      return plugin_jars[0], other_jars

  @property
  def _exported_symbols_plugin_classpath(self):
    return self.tool_classpath('exported-symbols-bootstrap')

  @property
  def _used_symbols_plugin_classpath(self):
    return self.tool_classpath('used-symbols-bootstrap')

  def _extract_exported_symbols(self, sources, java_runner):
    exported_symbols_by_source = {}
    bootclasspath = ':'.join(self._compiler_jars)
    full_plugin_cp = self._exported_symbols_plugin_classpath
    plugin_jar, classpath = self._split_out_plugin_jar_from_classpath(full_plugin_cp)
    jvm_options = [
      '-Xmx6g',
      '-Xss4096k',
      '-Xbootclasspath/a:{bootclasspath}'.format(bootclasspath=bootclasspath),
    ]
    scalac_args = [
      '-Xplugin:{0}'.format(plugin_jar),
      '-Ystop-after:emit-exported-symbols',
    ] + sources

    output_dir = tempfile.mkdtemp()
    try:
      jvm_options.append('-Dio.fsq.buildgen.plugin.emit_exported_symbols.outputDir={0}'
                         .format(output_dir))
      java_runner(classpath=classpath,
                  main=self._SCALAC_MAIN,
                  jvm_options=jvm_options,
                  args=scalac_args,
                  workunit_name='extract-exported-scala-symbols')
      output_files = [os.path.join(output_dir, f) for f in os.listdir(output_dir)]
      for output_file in output_files:
        with open(output_file, 'r') as f:
          source_symbol_json = json.loads(f.read())
          exported_symbols = source_symbol_json['symbols']
          package = source_symbol_json['package']
          source = os.path.relpath(source_symbol_json['source'], get_buildroot())
          exported_symbols_by_source[source] = {
            'package': package,
            'exported_symbols': list(set(exported_symbols) - set([package])),
          }
    finally:
      shutil.rmtree(output_dir)
    return exported_symbols_by_source

  def map_exported_symbols(self, sources, java_runner):
    def chunks(sources, chunk_size):
      for i in xrange(0, len(sources), chunk_size):
          yield sources[i:i + chunk_size]

    accumulated_sources = set()
    global_symbol_map = {}

    source_chunks = chunks(sources, 10000)
    for chunk in source_chunks:
      symbols_by_source = self._extract_exported_symbols(chunk, java_runner)
      global_symbol_map.update(symbols_by_source)

    return global_symbol_map

  def _extract_used_symbols(self, sources, whitelist_path, java_runner):
    used_symbols_by_source = {}
    bootclasspath = ':'.join(self._compiler_jars)
    full_plugin_cp = self._used_symbols_plugin_classpath
    plugin_jar, classpath = self._split_out_plugin_jar_from_classpath(full_plugin_cp)
    jvm_options = [
      '-Xmx6g',
      '-Xss4096k',
      '-Xbootclasspath/a:{bootclasspath}'.format(bootclasspath=bootclasspath),
      '-Dio.fsq.buildgen.plugin.used_symbol_emitter.whitelist={0}'.format(whitelist_path),
    ]
    scalac_args = [
      '-Xplugin:%s' % plugin_jar,
      '-Ystop-after:emit-used-symbols',
    ] + sources

    output_dir = tempfile.mkdtemp()
    try:
      jvm_options.append('-Dio.fsq.buildgen.plugin.used_symbol_emitter.outputDir={0}'
                         .format(output_dir))
      java_runner(classpath=classpath,
                  main=self._SCALAC_MAIN,
                  jvm_options=jvm_options,
                  args=scalac_args,
                  workunit_name='extract-used-scala-symbols')
      output_files = [os.path.join(output_dir, f) for f in os.listdir(output_dir)]
      for output_file in output_files:
        with open(output_file, 'r') as f:
          imported_symbols_json = json.loads(f.read())
          imported_symbols = [
            re.sub('^_root_\.', '', sym, count=1) for sym in imported_symbols_json['imports']
          ]
          fully_qualified_names = [
            re.sub('^_root_\.', '', name, count=1) for name in imported_symbols_json['fully_qualified_names']
          ]
          source = os.path.relpath(imported_symbols_json['source'], get_buildroot())
          analysis = {
            'imported_symbols': imported_symbols,
            'fully_qualified_names': fully_qualified_names,
          }
          used_symbols_by_source[source] = analysis
    finally:
      shutil.rmtree(output_dir)
    return used_symbols_by_source

  def map_used_symbols(self, sources, whitelist, java_runner):
    def chunks(sources, chunk_size):
      for i in xrange(0, len(sources), chunk_size):
          yield sources[i:i + chunk_size]

    accumulated_sources = set()
    global_symbol_map = {}

    source_chunks = chunks(sources, 10000)
    with tempfile.NamedTemporaryFile() as whitelist_file:
      whitelist_file.write('\n'.join(symbol.encode('utf-8') for symbol in whitelist))
      whitelist_file.flush()
      for chunk in source_chunks:
        symbols_by_source = self._extract_used_symbols(chunk,
                                                       whitelist_file.name,
                                                       java_runner)
        global_symbol_map.update(symbols_by_source)

    return global_symbol_map

