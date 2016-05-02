# coding=utf-8
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import

import pkgutil
import sys


python_third_party_map = {
  'apache': {
    'aurora': '3rdparty/python:apache.aurora.client',
  },
  'apscheduler': '3rdparty/python:APScheduler',
  'argcomplete': '3rdparty/python:argcomplete',
  'astroid': '3rdparty/python:astroid',
  'boto': '3rdparty/python:boto',
  'bson': '3rdparty/python:pymongo',
  'concurrent': '3rdparty/python:futures',
  'configobj': '3rdparty/python:configobj',
  'cookies': '3rdparty/python:cookies',
  'dateutil': '3rdparty/python:python-dateutil',
  'dns': '3rdparty/python:dnspython',
  'fake_filesystem': '3rdparty/python:pyfakefs',
  'fake_filesystem_glob': '3rdparty/python:pyfakefs',
  'fake_filesystem_shutil': '3rdparty/python:pyfakefs',
  'flask': '3rdparty/python:flask',
  'fs_cython_multilogistic_regression': '3rdparty/python:fs-cython-multilogistic-regression',
  'gen': {
    'apache': {
      'aurora': '3rdparty/python:apache.aurora.client',
    },
  },
  'google': {
    'protobuf': '3rdparty/python:protobuf',
  },
  'gunicorn': '3rdparty/python:gunicorn',
  'jsoncomment': '3rdparty/python:jsoncomment',
  'jsonschema': '3rdparty/python:jsonschema',
  'kafka': '3rdparty/python:kafka-python',
  'kazoo': '3rdparty/python:kazoo',
  'keyczar': '3rdparty/python:python-keyczar',
  'luigi': '3rdparty/python:luigi',
  'lvm': '3rdparty/python/linuxonly:lvm',
  'lxml': '3rdparty/python:lxml',
  'mako': '3rdparty/python:Mako',
  'mock': '3rdparty/python:mox',
  'motor': '3rdparty/python:motor',
  'mox': '3rdparty/python:mox',
  'path': '3rdparty/python:path',
  'pep8': '3rdparty/python:pep8',
  'phabricator': '3rdparty/python:phabricator',
  'psycopg2': '3rdparty/python:psycopg2',
  'pybindxml': '3rdparty/python:pybindxml',
  'pycurl': '3rdparty/python:pycurl',
  'pymongo': '3rdparty/python:pymongo',
  'pymysql': '3rdparty/python:PyMySQL',
  'pysnmp': '3rdparty/python:pysnmp',
  'pystache': '3rdparty/python:pystache',
  'pytest': '3rdparty/python:pytest',
  'redis': '3rdparty/python:Redis',
  'repoze': '3rdparty/python:repoze.lru',
  'requests': '3rdparty/python:requests',
  'requests_futures': '3rdparty/python:requests-futures',
  'scrapy': '3rdparty/python:scrapy',
  'simplejson': '3rdparty/python:simplejson',
  'six': '3rdparty/python:six',
  'sqlalchemy': '3rdparty/python:SQLAlchemy',
  'supervisor': '3rdparty/python:supervisor',
  'thrift': '3rdparty/python:thrift',
  'tornado': '3rdparty/python:tornado',
  'tornadoredis': '3rdparty/python:tornado-redis',
  'toro': '3rdparty/python:toro',
  'twisted': '3rdparty/python:Twisted',
  'twitter': {
    'common': {
      'collections': '3rdparty/python:twitter.common.collections',
      'confluence': '3rdparty/python:twitter.common.confluence',
      'dirutil': '3rdparty/python:twitter.common.dirutil',
    },
  },
  'whoops': '3rdparty/python:whoops',
  'yaml': '3rdparty/python:PyYAML',
}


def get_system_modules(first_party_packages):
  """Return the list of all loaded modules that are not declared as first or third party libraries.

  Callers should cache this return value instead of recalculating repeatedly.
  :param list first_party_packages: A list of all package names produced by this repo, e.g. ['foursquare', 'fsqio']).
  """
  # Get list of all loaded modules.
  loaded_modules = [m for _, m, _ in list(pkgutil.iter_modules())]
  interpreter_modules = list(sys.builtin_module_names)
  modules = sorted(loaded_modules + interpreter_modules)

  # Filter out all modules that are declared as first or third party packages.
  return sorted([m for m in modules if m not in python_third_party_map and m not in first_party_packages])
