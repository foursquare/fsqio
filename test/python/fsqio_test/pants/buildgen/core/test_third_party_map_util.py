# coding=utf-8
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function, unicode_literals

from copy import deepcopy
import unittest

from pants.util.memo import memoized_property

from fsqio.pants.buildgen.core.third_party_map_util import merge_map


class TestThirdPartyMapUtil(unittest.TestCase):
  # NOTE(mateo): This should be expanded to test the actual interop with Pants. But we are taking baby-steps
  # towards being able to extend upstream Pants test infa. It is just not worth reinventing the wheel for all the
  # before that happens. So for now, this just tests operations that are done outside of Pants.

  # TODO(mateo): Test the check_if_manually_defined logic.

  @memoized_property
  def _default(self):
    return {
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

  @memoized_property
  def _additional(self):
    return {
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

  @memoized_property
  def _expected_merge_results(self):
    # This is just the expected returned map when you run the fixture.
    return {
      'ansicolors': '/elsewhere:ansicolors',
      'apache': '/elsewhere:apache.aurora.client',
      'boto': None,
      'fsqio': {
        'buildgen': {
          'core': '/elsewhere:buildgen-core',
          'python': '/elsewhere:buildgen-jvm'}},
      'twitter': {
        'common': {
          'collections': '/elsewhere:twitter.common.collections',
          'confluence': '/elsewhere:twitter.common.confluence',
          'dirutil': '3rdparty/python:twitter.common.dirutil',
          'log': '/elsewhere:twitter.common.log'
        },
         'finagle': {
           'DEFAULT': 'a_bird_name'
          }
        },
      'yaml': '/elsewhere/python:PyYAML'
    }

  def _map_fixture(self):
    return self._default, self._additional

  def test_merge_output(self):
    # Maybe not so useful as a unittest, but good for an example to module consumers.
    # Remember - the merge has the not great property of updating the map in place - so default will become merged.
    default, additional = self._map_fixture()
    self.assertNotEqual(default, self._expected_merge_results)

    # Do the merge, now should be equal.
    merge_map(default, additional)
    self.assertEqual(default, self._expected_merge_results)

  def test_merge_empty(self):
    # No changes are made to the map if it is merged with an empty dict.
    default, _ = self._map_fixture()
    copied = deepcopy(default)

    self.assertEqual(default, copied)
    merge_map(default, {})
    self.assertEqual(default, copied)

  def test_merge_value_of_none(self):
    default, additional = self._map_fixture()
    merge_map(default, additional)
    self.assertEqual(default['boto'], None)

  def test_merge_contains_all_keys(self):
    default, additional = self._map_fixture()
    # A pure update - this only tells us that the top-level map entries are as expected.
    copied_map = deepcopy(default)
    copied_map.update(additional)

    merge_map(default, additional)
    self.assertEqual(len(copied_map), len(default))
    self.assertEqual(sorted(copied_map.keys()), sorted(default.keys()))

  def test_merge_simple_override(self):
    default, additional = self._map_fixture()
    # Store a copy of additional to show it is not modified.
    stored_additional = deepcopy(additional)
    self.assertEqual(additional, stored_additional)

    self.assertNotEqual(default['ansicolors'], additional['ansicolors'])
    merge_map(default, additional)
    self.assertEqual(default['ansicolors'], additional['ansicolors'])
    self.assertEqual(additional, stored_additional)

  def test_merge_nested_overrides(self):
    default, additional = self._map_fixture()
    self.assertEqual(default['twitter']['common']['log'], '3rdparty/python:twitter.common.log')
    merge_map(default, additional)

    # Test that entries that are defined in the additional_map are updated.
    self.assertEqual(default['twitter']['common']['log'], '/elsewhere:twitter.common.log')

    # If both subtrees have the same height, the leaf should only change if explicitly overriden.
    self.assertEqual(default['twitter']['common']['dirutil'], '3rdparty/python:twitter.common.dirutil')

  def test_merge_clobber_subtree(self):
    # If an additional_map entry has less levels than the default_map, then the replace the entire subtree with the
    # additional map entry.

    # A use case would be wanting to override all apache entries to be one library, no matter how many levels are
    # defined by the default_map.
    default, additional = self._map_fixture()
    self.assertTrue(isinstance(default['apache'], dict))
    merge_map(default, additional)
    self.assertTrue(isinstance(default['apache'], unicode))
    self.assertEqual(default['apache'], additional['apache'])

  def test_merge_new_keys(self):
    default, additional = self._map_fixture()
    self.assertNotIn('fsqio', default)
    merge_map(default, additional)
    self.assertIn('fsqio', default)
    self.assertIn('buildgen', default['fsqio'])
    self.assertTrue(isinstance(default['fsqio'], dict))

  def test_merge_after_nested_entry(self):
    # Make sure that any recursive function calls return to finish the final entries.
    default, additional = self._map_fixture()
    self.assertNotEqual(default['yaml'], additional['yaml'])
    merge_map(default, additional)
    self.assertEqual(default['yaml'], additional['yaml'])
    self.assertEqual(additional['yaml'], '/elsewhere/python:PyYAML')

  def test_merge_default(self):
    # If the additional_map defines a DEFAULT, then we assume that the additional_map defines the world of accepted
    # mappings and only use the additional_map entries for that subtree.
    default, additional = self._map_fixture()

    self.assertEqual(default['twitter']['finagle']['memcached']['clazz'], '3rdparty/python:finagle-memcached')
    self.assertGreater(len(default['twitter']['finagle']), 1)

    merge_map(default, additional)

    # Since additional_map has 'DEFAULT' defined, all deeper nodes in the original map are discarded if not explictly
    # included in the additional_map.
    self.assertEqual(len(default['twitter']['finagle']), 1)
    self.assertIn('DEFAULT', default['twitter']['finagle'])
    self.assertEqual(default['twitter']['finagle']['DEFAULT'], 'a_bird_name')
