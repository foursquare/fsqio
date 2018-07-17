# coding=utf-8
# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function, unicode_literals

import os

from pants.base.exceptions import TaskError
from pants.util.contextutil import open_zip, temporary_dir, temporary_file
from pants_test.jvm.jar_task_test_base import JarTaskTestBase

from fsqio.pants.buildgen.jvm.core.map_third_party_jar_symbols import MapThirdPartyJarSymbols


class TestMapThirdPartyJarSymbols(JarTaskTestBase):

  @staticmethod
  def _calculate_importable(source_files):
    importables = set()
    for file_name in source_files:
      package_path, _ = os.path.splitext(file_name)
      importable = package_path.replace('/', '.')
      importables.add(importable)
    return importables

  @classmethod
  def task_type(cls):
    return MapThirdPartyJarSymbols

  def setUp(self):
    super(TestMapThirdPartyJarSymbols, self).setUp()
    self.task = self.prepare_execute(self.context())
    self.default_files = ['a/b/c/Foo.class', 'a/b/c/Bar.class']

  def create_a_jarfile(self, location, name, filenames=None):
    # Create a sample jar file. Will populate the default files, or override and instead use the filenames list.
    name = '{}.jar'.format(name)
    jar_name = os.path.join(location, name)
    with open_zip(jar_name, 'w') as jarfile:
      file_list = filenames if filenames else self.default_files
      for file_name in file_list:
        jarfile.writestr(file_name, '0xCAFEBABE')
    return jar_name

  def test_dump_jar_contents(self):
    with temporary_dir() as temp:
      jarfile = self.create_a_jarfile(temp, 'test.jar')
      self.assertEqual(self.task._dump_jar_contents(jarfile), self.default_files)

  def test_dump_jar_contents_bad_zip(self):
    with temporary_file() as corrupted:
      with self.assertRaisesRegexp(TaskError, r'{}'.format(os.path.realpath(corrupted.name))):
        self.task._dump_jar_contents(corrupted.name)

  def test_fully_qualified_class_names(self):
    with temporary_dir() as temp:
      jarfile = self.create_a_jarfile(temp, 'test.jar')
      importables = self._calculate_importable(self.default_files)
      self.assertEqual(self.task.fully_qualified_classes_from_jar(jarfile), importables)

  def test_fully_qualified_class_names_filters_nonimportable_classnames(self):
    with temporary_dir() as temp:
      importable_source = ['a/b/c/Foo.class']
      nonimportable_source = ['a/b/1.class']
      mixed_bag = importable_source + nonimportable_source
      jarfile = self.create_a_jarfile(temp, 'test.jar', filenames=mixed_bag)

      should_match = self._calculate_importable(importable_source)
      should_not_match = self._calculate_importable(nonimportable_source)

      matched_classes = self.task.fully_qualified_classes_from_jar(jarfile)
      self.assertEqual(should_match, matched_classes)
      self.assertNotIn(should_not_match.pop(), matched_classes)

  def test_fully_qualified_class_names_filters_resources(self):
    with temporary_dir() as temp:
      resources = ['your_file.png', 'my_file.txt']
      jarfile = self.create_a_jarfile(temp, 'test.jar', filenames=resources)
      self.assertFalse(self.task.fully_qualified_classes_from_jar(jarfile))
