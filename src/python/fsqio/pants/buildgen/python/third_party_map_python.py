# coding=utf-8
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import

import distutils
import glob
import os
import pkgutil
import sys


def is_importable(root, name, exts=()):
  extensions = exts or ('py', 'so', 'pyd')
  attempt = os.path.join(root, name)
  if os.path.isdir(attempt) or os.path.isfile(attempt):
    return True
  for ext in extensions:
    if os.path.isfile('{}.{}'.format(attempt, ext)):
      return True
  return False


def get_third_party_modules(venv_root, dep_map):

  def walk_module_tree(dep, root, import_path, routes=None):
    # Recursively walk a file tree, mapping the relpath(root, directory) of every matched directory to the passed dep.
    if routes is None:
      routes = {}
    path = os.path.join(root, import_path)
    if os.path.isdir(path):
      for child in os.listdir(path):
        if not child.startswith('_'):
          child_path = os.path.join(import_path, child)
          walk_module_tree(dep, root, child_path, routes)
      routes[import_path] = dep
    return routes

  allowed_import_prefixes = {}
  import_map = {}

  if venv_root:
    site_packages_root = os.path.join(venv_root, 'site-packages')
    if not os.path.isdir(site_packages_root):
      raise Exception("There is no site-packages dir at: {}".format(venv_root))
    for dep in dep_map:

      if '.' in dep:
        # Treated as top_level or else we risk bringing in their transitive deps or clobbering other valid imports.
        top_level = [dep.replace('.', '/')]
      else:
        # Parse file distributed with each package that defines the top level dirs or files for each module.
        globbed_files = list(glob.iglob(os.path.join(site_packages_root, dep + '-*-info', 'top_level.txt')))
        top_level = map(str.strip, open(list(globbed_files)[0]).readlines()) if globbed_files else []
      if not top_level:
        # Rarely there's no dist-info or top_level, then we have to accept the PyPi name transformed to valid import.
        _, package_name = dep_map[dep].lower().replace('-', '_').split(':')
        top_level = [package_name]

      for top in top_level:

        if top.startswith('_') or not is_importable(site_packages_root, top):
          continue
        route = walk_module_tree(dep_map[dep], site_packages_root, top)
        if not route:
          # Since we know it is importable, that means there is a top-level file with that importable name.
          route = {top: dep_map[dep]}
        import_map.update(route)
        # We do not raise Exception if a target has no valid imports because it may be a platform-specific module.

    # Convert verified file paths into valid import strings.
    for route, target in import_map.items():
      import_path, _ = os.path.splitext(route)
      proper_import = import_path.replace('/', '.')
      allowed_import_prefixes[proper_import] = target
  return allowed_import_prefixes


def get_system_modules():
  """Return the Python builtins and stdlib top_level import names for a distribution."""

  # Get list of all loaded source modules.
  modules = {module for _, module, package in list(pkgutil.iter_modules()) if package is False}

  # Gather the import names from the site-packages installed in the pants-virtualenv.
  module_names = glob.iglob(os.path.join(os.path.dirname(os.__file__), 'site-packages', '*-*', 'top_level.txt'))
  site_packages = [map(str.strip, open(txt).readlines()) for txt in list(module_names)]

  for packages in site_packages:
    modules -= set(packages)

  # Get the system packages.
  system_modules = set(sys.builtin_module_names)

  # Get the top-level packages from the python install (email, logging, xml, some others).
  _, top_level_libs, _ = list(os.walk(distutils.sysconfig.get_python_lib(standard_lib=True)))[0]
  return sorted(top_level_libs + list(modules | system_modules))


# TODO(mateo): This has outgrown its roots as a simple python script. Productionize into a task or class.
def get_venv_map(venv_roots, dep_map):
  venv_map = {}
  venv_map['python_modules'] = get_system_modules()
  site_map = {}
  for venv in venv_roots:
    site_map.update(get_third_party_modules(venv, dep_map))
  venv_map['third_party'] = site_map
  return venv_map
