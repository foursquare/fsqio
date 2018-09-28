# coding=utf-8
# Copyright 2018 Foursquare Labs Inc. All Rights Reserved.

from __future__ import (
  absolute_import,
  division,
  generators,
  nested_scopes,
  print_function,
  unicode_literals,
  with_statement,
)

import json
import os

from pants.backend.project_info.tasks.export import ExportTask, get_buildroot
from pants.task.console_task import ConsoleTask


# Changing the behavior of this task may affect the IntelliJ Pants plugin.
# Please add @yic to reviews for this file.
class GenStubsAndExportTask(ExportTask):
  """Base class for generating a json-formattable blob of data about the target graph.

  Subclasses can invoke the generate_targets_map method to get a dictionary of plain datastructures
  (dicts, lists, strings) that can be easily read and exported to various formats.
  Note this is subclassed form the original export, hot swapping sources for generated stubs.
  """

  @classmethod
  def prepare(cls, options, round_manager):
    # TODO: this method is overriden explicitly for stub generation. When we change the approach we can remove
    super(GenStubsAndExportTask, cls).prepare(options, round_manager)
    if options.libraries or options.libraries_sources or options.libraries_javadocs:
      round_manager.require_data('stubs')

  @staticmethod
  def _source_roots_for_target(target):
    def mod_path_to_ide_gen(path):
      """
      :param path:
      :return: new path with the ide-gen location
      """
      path_components = path.split('/')
      try:
        anchor_idx = path_components.index('spindle')
      except ValueError:
        # not a spindle file, just return original path
        return path

      path_components[anchor_idx - 1] = 'ide-gen'
      path_components[anchor_idx] = 'spindle-stubs'
      path_components[anchor_idx + 1] = 'current'
      return '/'.join(path_components)

    def root_package_prefix(source_file):
      source = os.path.dirname(source_file)
      if target.is_synthetic:
        source_root = mod_path_to_ide_gen(os.path.join(get_buildroot(), target.target_base, source))

      else:
        source_root = os.path.join(get_buildroot(), target.target_base, source)
      return source_root, source.replace(os.sep, '.')

    return set(map(root_package_prefix, target.sources_relative_to_source_root()))


class GenStubsAndExport(GenStubsAndExportTask, ConsoleTask):
  """Export project information in JSON format.

  Intended for exporting project information for IDE, such as the IntelliJ Pants plugin.
  TODO: we can back this out entirely when we introduce stubs to pants more formally.
  """

  def __init__(self, *args, **kwargs):
    super(GenStubsAndExport, self).__init__(*args, **kwargs)

  def console_output(self, targets, classpath_products=None):
    graph_info = self.generate_targets_map(targets, classpath_products=classpath_products)
    if self.get_options().formatted:
      return json.dumps(graph_info, indent=4, separators=(',', ': ')).splitlines()
    else:
      return [json.dumps(graph_info)]
