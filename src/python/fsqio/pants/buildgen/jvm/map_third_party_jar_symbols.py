# coding=utf-8
# Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

from __future__ import (
  absolute_import,
  division,
  generators,
  nested_scopes,
  print_function,
  unicode_literals,
  with_statement,
)

from contextlib import closing
from itertools import chain
import json
import os
import re
from zipfile import ZipFile

from pants.backend.jvm.targets.jar_library import JarLibrary
from pants.invalidation.cache_manager import VersionedTargetSet
from pants.task.task import Task
from pants.util.dirutil import safe_mkdir


class MapThirdPartyJarSymbols(Task):

  @classmethod
  def product_types(cls):
    return [
      'third_party_jar_symbols',
    ]

  def __init__(self, *args, **kwargs):
    super(MapThirdPartyJarSymbols, self).__init__(*args, **kwargs)

  @classmethod
  def prepare(cls, options, round_manager):
    super(MapThirdPartyJarSymbols, cls).prepare(options, round_manager)

    # NOTE(mateo): This is a deprecated concept upstream - everything is in the classpath now. So it will take some
    # fiddling to get the jar symbols for anyone not using pom-resolve.
    round_manager.require_data('ivy_resolve_symlink_map')
    round_manager.require_data('java')
    round_manager.require_data('scala')

  CLASSFILE_RE = re.compile(r'(?P<path_parts>(?:\w+/)+)'
                            r'(?P<file_part>.*?)'
                            r'\.class')
  CLASS_NAME_RE = re.compile(r'[a-zA-Z]\w*')

  def fully_qualified_classes_from_jar(self, jar_abspath):
    with closing(ZipFile(jar_abspath)) as dep_zip:
      for qualified_file_name in dep_zip.namelist():
        match = self.CLASSFILE_RE.match(qualified_file_name)
        if match is not None:
          file_part = match.groupdict()['file_part']
          path_parts = match.groupdict()['path_parts']
          path_parts = filter(None, path_parts.split('/'))
          package = '.'.join(path_parts)
          non_anon_file_part = file_part.split('$$')[0]
          nested_classes = non_anon_file_part.split('$')
          for i in range(len(nested_classes)):
            if not self.CLASS_NAME_RE.match(nested_classes[i]):
              break
            nested_class_name = '.'.join(nested_classes[:i + 1])
            fully_qualified_class = '.'.join([package, nested_class_name])
            yield fully_qualified_class

  def execute(self):
    products = self.context.products
    targets = self.context.targets(lambda t: isinstance(t, JarLibrary))

    with self.invalidated(targets, invalidate_dependents=False) as invalidation_check:
      global_vts = VersionedTargetSet.from_versioned_targets(invalidation_check.all_vts)
      vts_workdir = os.path.join(self._workdir, global_vts.cache_key.hash)
      vts_analysis_file = os.path.join(vts_workdir, 'buildgen_analysis.json')
      if invalidation_check.invalid_vts or not os.path.exists(vts_analysis_file):
        all_jars = products.get_data('ivy_resolve_symlink_map').values()
        calculated_analysis = {}
        calculated_analysis['hash'] = global_vts.cache_key.hash
        calculated_analysis['jar_to_symbols_exported'] = {}
        for jar_path in sorted(all_jars):
          if os.path.splitext(jar_path)[1] != '.jar':
            continue
          fully_qualified_classes = list(set(self.fully_qualified_classes_from_jar(jar_path)))
          calculated_analysis['jar_to_symbols_exported'][jar_path] = {
            'fully_qualified_classes': fully_qualified_classes,
          }
        calculated_analysis_json = json.dumps(calculated_analysis)
        safe_mkdir(vts_workdir)
        with open(vts_analysis_file, 'wb') as f:
          f.write(calculated_analysis_json)
        if self.artifact_cache_writes_enabled():
          self.update_artifact_cache([(global_vts, [vts_analysis_file])])
      with open(vts_analysis_file, 'rb') as f:
        analysis = json.loads(f.read())

      third_party_jar_symbols = set(chain.from_iterable(
        v['fully_qualified_classes'] for v in analysis['jar_to_symbols_exported'].values()
      ))
      products.safe_create_data('third_party_jar_symbols', lambda: third_party_jar_symbols)

  def check_artifact_cache_for(self, invalidation_check):
    # Pom-resolve is an output dependent on the entire target set, and is not divisible
    # by target. So we can only cache it keyed by the entire target set.
    global_vts = VersionedTargetSet.from_versioned_targets(invalidation_check.all_vts)
    return [global_vts]
