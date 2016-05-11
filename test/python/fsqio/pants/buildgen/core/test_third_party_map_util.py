# coding=utf-8
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

from __future__ import (
  absolute_import,
  division,
  generators,
  nested_scopes,
  print_function,
  unicode_literals,
  with_statement,
)

from copy import deepcopy
import unittest

from fsqio.pants.buildgen.core.third_party_map_util import merge_map


class TestThirdPartyMapUtil(unittest.TestCase):


  # NOTE(mateo): This should be expanded to test the actual interop with Pants. But we are taking baby-steps
  # towards being able to extend upstream Pants test infa. It is just not worth reinventing the wheel for all the
  # before that happens. So for now, this just tests operations that are done outside of Pants.

  # TODO(mateo): Test the check_if_manually_defined logic.

  def setUp(self):
    self.default_map = {
      'ansicolors': '3rdparty/python:ansicolors',
      'apache': {
        'aurora': '3rdparty/python:apache.aurora.client',
      },
      'boto': None,
      'twitter': {
        'common': {
          'collections': '3rdparty/python:twitter.common.collections',
          'confluence': '3rdparty/python:twitter.common.confluence',
          'dirutil': '3rdparty/python:twitter.common.dirutil',
          'log': '3rdparty/python:twitter.common.log',
        },
        'finagle': {
          'memcached': {
            'clazz': '3rdparty/python:finagle-memcached',
          },
          'DEFAULT': 'finagle',
        },
      },
      'yaml': '3rdparty/python:PyYAML',
    }

    self.additional_map =  {
       'ansicolors': '/elsewhere:ansicolors',
       'apache': '/elsewhere:apache.aurora.client',
       'fsqio': {
         'buildgen': {
            'core': '/elsewhere:buildgen-core',
            'python': '/elsewhere:buildgen-jvm',
          },
       },
       'twitter': {
          'common': {
            'collections': '/elsewhere:twitter.common.collections',
            'confluence': '/elsewhere:twitter.common.confluence',
            'log': '/elsewhere:twitter.common.log',
          },
          'finagle': {
            'DEFAULT': 'a_bird_name',
          }
       },
       'yaml': '/elsewhere/python:PyYAML',
     }

  def test_merge_output(self):
    # Maybe not so useful as a unittest, but good for an example to module consumers.
    merge_map(self.default_map, self.additional_map)
    expected_merged_map = {
      'ansicolors': '/elsewhere:ansicolors',
      'apache': '/elsewhere:apache.aurora.client',
      'boto': None,
      'fsqio': {
        'buildgen': {
          'core':  '/elsewhere:buildgen-core',
          'python':  '/elsewhere:buildgen-jvm'}},
      'twitter': {
        'common': {
          'collections':  '/elsewhere:twitter.common.collections',
          'confluence':  '/elsewhere:twitter.common.confluence',
          'dirutil':  '3rdparty/python:twitter.common.dirutil',
          'log':  '/elsewhere:twitter.common.log'
        },
         'finagle': {
           'DEFAULT':  'a_bird_name'
          }
        },
        'yaml':  '/elsewhere/python:PyYAML'
    }
    self.assertEqual(self.default_map, expected_merged_map)

  def test_merge_empty(self):
    additional_map = {}
    self.assertEqual(self.default_map, merge_map(self.default_map, additional_map))

  def test_merge_value_of_none(self):
    merge_map(self.default_map, self.additional_map)
    self.assertEqual(self.default_map['boto'], None)

  def test_merge_contains_all_keys(self):
    copied_map = deepcopy(self.default_map)
    copied_map.update(self.additional_map)

    merge_map(self.default_map, self.additional_map)
    self.assertEqual(len(copied_map), len(self.default_map))

  def test_merge_simple_override(self):
    self.assertNotEqual(self.default_map['ansicolors'], self.additional_map['ansicolors'])
    merge_map(self.default_map, self.additional_map)
    self.assertEqual(self.default_map['ansicolors'], self.additional_map['ansicolors'])

  def test_merge_nested_overrides(self):
    self.assertEqual(self.default_map['twitter']['common']['log'], '3rdparty/python:twitter.common.log')
    merge_map(self.default_map, self.additional_map)

    # Test that entries that are defined in the additional_map are updated.
    self.assertEqual(self.default_map['twitter']['common']['log'], '/elsewhere:twitter.common.log')

    # If both subtrees have the same height, the leaf should only change if explicitly overriden.
    self.assertEqual(self.default_map['twitter']['common']['dirutil'], '3rdparty/python:twitter.common.dirutil')

  def test_merge_clobber_subtree(self):
    # If an additional_map entry has less levels than the default_map, then the replace the entire subtree with the
    # additional map entry.

    # A use case would be wanting to override all apache entries to be one library, no matter how many levels are
    # defined by the default_map.
    self.assertTrue(isinstance(self.default_map['apache'], dict))
    merge_map(self.default_map, self.additional_map)
    self.assertTrue(isinstance(self.default_map['apache'], unicode))
    self.assertEqual(self.default_map['apache'], self.additional_map['apache'])

  def test_merge_new_keys(self):
    self.assertNotIn('fsqio', self.default_map)
    merge_map(self.default_map, self.additional_map)
    self.assertIn('fsqio', self.default_map)
    self.assertIn('buildgen', self.default_map['fsqio'])
    self.assertTrue(isinstance(self.default_map['fsqio'], dict))

  def test_merge_after_nested_entry(self):
    # Make sure that any recursive function calls return to finish the final entries.
    self.assertNotEqual(self.default_map['yaml'], self.additional_map['yaml'])
    merge_map(self.default_map, self.additional_map)
    self.assertEqual(self.default_map['yaml'], self.additional_map['yaml'])
    self.assertEqual(self.additional_map['yaml'], '/elsewhere/python:PyYAML')

  def test_merge_default(self):
    # If the additional_map defines a DEFAULT, then we assume that the additional_map defines the world of accepted
    # mappings and only use the additional_map entries for that subtree.
    self.assertEqual(self.default_map['twitter']['finagle']['memcached']['clazz'], '3rdparty/python:finagle-memcached')
    self.assertGreater(len(self.default_map['twitter']['finagle']), 1)

    merge_map(self.default_map, self.additional_map)

    # Since additional_map has 'DEFAULT' defined, all deeper nodes in the original map are discarded if not explictly
    # included in the additional_map.
    self.assertEqual(len(self.default_map['twitter']['finagle']), 1)
    self.assertIn('DEFAULT', self.default_map['twitter']['finagle'])
    self.assertEqual(self.default_map['twitter']['finagle']['DEFAULT'], 'a_bird_name')

