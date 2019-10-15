# coding=utf-8
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function, unicode_literals

import contextlib
import json
import os

from future.utils import viewitems
from pants.base.exceptions import TaskError
from pants.base.workunit import WorkUnitLabel
from pants.contrib.node.subsystems.resolvers.npm_resolver import NpmResolver
from pants.contrib.node.tasks.node_resolve import NodeResolve
from pants.util.contextutil import pushd

from fsqio.pants.node.targets.webpack_module import NpmResource, WebPackModule


class WebPackResolver(NpmResolver):
  """Subsystem to resolve the webpack_modules."""
  options_scope = 'webpack-resolver'

  # All keys in package.json that may point to dependencies.
  _dependencies_keys = [
    'dependencies', 'devDependencies', 'peerDependencies', 'optionalDependencies'
  ]

  @classmethod
  def register_options(cls, register):
    NodeResolve.register_resolver_for_type(WebPackModule, cls)
    NodeResolve.register_resolver_for_type(NpmResource, cls)
    super(WebPackResolver, cls).register_options(register)

  def resolve_target(self, node_task, target, results_dir, node_paths):
    self._copy_sources(target, results_dir)
    with pushd(results_dir):
      if not os.path.exists('package.json'):
        raise TaskError(
          'Cannot find package.json. Did you forget to put it in target sources?')
        if os.path.exists('package-lock.json'):
          node_task.context.log.info('Found package-lock.json, will not inject package.json')
        else:
          node_task.context.log.warn(
            'Cannot find package-lock.json. Did you forget to put it in target sources? '
            'This package will fall back to inject package.json with pants BUILD dependencies '
            'including node_remote_module and other node dependencies. However, this is '
            'not fully supported.')
          self._emit_package_descriptor(node_task, target, results_dir, node_paths)
      self._rewrite_package_descriptor(node_task, target, results_dir, node_paths)

      result, command = node_task.install_module(
        target=target,
        install_optional=self.get_options().install_optional,
        workunit_name=target.address.reference(),
        workunit_labels=[WorkUnitLabel.COMPILER])
      if result != 0:
        raise TaskError('Failed to resolve dependencies for {}:\n\t{} failed with exit code {}'
                        .format(target.address.reference(), command, result))

  def _remove_file_uris_from_dependencies(self, package):
    """Remove file: URIs from any dependencies in package.json."""
    for key in self._dependencies_keys:
      if key in package:
        dependencies = package[key]
        filtered_dependencies = {
          name: spec for (name, spec) in viewitems(dependencies)
          if not spec.startswith('file:')
        }
        package[key] = filtered_dependencies

  @contextlib.contextmanager
  def _json_file(self, path):
    """Context manager that loads a JSON file, lets you manipulate any fields, and then writes it out again."""
    data = {}
    if os.path.isfile(path):
      with open(path, 'r') as fp:
        data = json.load(fp)

    yield data

    with open(path, 'wb') as fp:
      json.dump(data, fp, indent=2)

  def _rewrite_package_descriptor(self, node_task, target, results_dir, node_paths):
    package_json_path = os.path.join(results_dir, 'package.json')
    package_lock_json_path = os.path.join(results_dir, 'package-lock.json')

    def version_or_path(dep):
      return node_paths.node_path(dep) if node_task.is_node_module(dep) else dep.version

    with self._json_file(package_json_path) as package:
      if 'name' not in package:
        package['name'] = target.package_name
      elif package['name'] != target.package_name:
        raise TaskError('Package name in the corresponding package.json is not the same '
                        'as the BUILD target name for {}'.format(target.address.reference()))

      self._remove_file_uris_from_dependencies(package)

      # Add in any dependencies from the BUILD file.
      dependencies_to_add = {
        dep.package_name: version_or_path(dep) for dep in target.dependencies
      }
      if 'dependencies' not in package:
        package['dependencies'] = {}
      package['dependencies'].update(dependencies_to_add)

    with self._json_file(package_lock_json_path) as package_lock_json:
      # Modify the `version` field in package-lock.json with the updated path
      for dep in target.dependencies:
        if dep.package_name in package_lock_json['dependencies']:
          package_lock_json['dependencies'][dep.package_name]['resolved'] = version_or_path(dep)
