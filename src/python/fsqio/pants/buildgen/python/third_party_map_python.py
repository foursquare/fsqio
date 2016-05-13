# coding=utf-8
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import

import distutils
import glob
import os
import pkgutil
import sys


python_third_party_map = {
  'apache': {
    'aurora': '3rdparty/python:apache.aurora.client',
  },
  'bson': '3rdparty/python:pymongo',
  'colors': '3rdparty/python:ansicolors',
  'concurrent': '3rdparty/python:futures',
  'dateutil': '3rdparty/python:python-dateutil',
  'dns': '3rdparty/python:dnspython',
  'fake_filesystem': '3rdparty/python:pyfakefs',
  'fake_filesystem_glob': '3rdparty/python:pyfakefs',
  'fake_filesystem_shutil': '3rdparty/python:pyfakefs',
  'gen': {
    'apache': {
      'aurora': '3rdparty/python:apache.aurora.client',
    },
  },
  'google': {
    'protobuf': '3rdparty/python:protobuf',
  },
  'fake_filesystem': '3rdparty/python:pyfakefs',
  'fake_filesystem_glob': '3rdparty/python:pyfakefs',
  'fake_filesystem_shutil': '3rdparty/python:pyfakefs',
  'kafka': '3rdparty/python:kafka-python',
  'keyczar': '3rdparty/python:python-keyczar',
  'repoze': {
    'lru': '3rdparty/python:repoze.lru',
  },
  'tornadoredis': '3rdparty/python:tornado-redis',
  'twitter': {
    'common': {
      'collections': '3rdparty/python:twitter.common.collections',
      'confluence': '3rdparty/python:twitter.common.confluence',
      'dirutil': '3rdparty/python:twitter.common.dirutil',
      'log': '3rdparty/python:twitter.common.log',
    },
  },
  'yaml': '3rdparty/python:PyYAML',
}


def get_system_modules():
  """Return the Python builtins and stdlib top_level import names for a distribution."""

  # Get list of all loaded source modules.
  modules = {module for _, module, package in list(pkgutil.iter_modules()) if package is False}

  # Gather the import names from the site-packages installed in the pants-virtualenv.
  top_level_txt = glob.iglob(os.path.join(os.path.dirname(os.__file__) + '/site-packages', '*-info', 'top_level.txt'))
  site_packages = [map(str.strip, open(txt).readlines()) for txt in list(top_level_txt)]

  user_modules = set()
  for packages in site_packages:
    # 'pip' and 'setuptools' packages are dependencies of the Pants virtualenv so we treat them as guaranteed.
    if 'pip' not in packages and 'setuptools' not in packages:
      user_modules.update(set(packages))
    else:
      modules.update(packages)

  # Delete the site-packages from the module lists.
  modules -= user_modules

  # Get the system packages.
  system_modules = set(sys.builtin_module_names)

  # Get the top-level packages from the python install (email, logging, xml, some others).
  _, top_level_libs, _ = list(os.walk(distutils.sysconfig.get_python_lib(standard_lib=True)))[0]
  return sorted(top_level_libs + list(modules | system_modules))
