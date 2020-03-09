# coding=utf-8
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function, unicode_literals

import collections

from builtins import str
import configparser


def merge_map(a, b):
  """Recursively merge two nested dictionaries, with any given subtree in b recursively taking priority over a."""
  # Callers are encouraged to pass a deepcopy of dict a.
  # TODO(mateo): Remove mutation and return a new dict instead.

  # Recursion terminates on any type that isn't a dictionary, which means that values in b can clobber
  # entire subtrees of a. I consider that to be the correct thing to do.

  if not isinstance(a, dict) or not isinstance(b, dict):
    return b

  # If a b subtree defines a 'DEFAULT' then assume that it maps the world of b's acceptable mappings for that subtree.
  if 'DEFAULT' in b:
    a.clear()
  for key in b.keys():
    a[key] = merge_map(a[key], b[key]) if key in a else b[key]
  return a


def check_manually_defined(symbol, subtree=None):
  """Checks to see if a symbol was hand mapped by third_party_map."""

  if subtree == 'SKIP':
    return 'SKIP'
  if isinstance(subtree, basestring) or subtree is None:
    return subtree

  parts = symbol.split('.')
  if not parts:
    raise ValueError('Came to the end of the symbol while still traversing tree.  Subtree '
                     'keys were {keys}'.format(keys=subtree.keys()))
  if parts[0] not in subtree:
    if 'DEFAULT' in subtree:
      return subtree['DEFAULT']
    else:
      return None
  else:
    return check_manually_defined('.'.join(parts[1:]), subtree[parts[0]])


def read_config_map(config_path, map_type):
  config = configparser.RawConfigParser()
  config.optionxform = str
  config.read(str(config_path))
  config_for_type = config[map_type]
  return flat_to_tree(config_for_type)


def flat_to_tree(third_party_flat):
  third_party_map = {}
  for k, v in third_party_flat.items():
    k_split = k.split('.')
    curr_items = third_party_map
    for k_part in k_split[:-1]:
      if k_part not in curr_items:
        curr_items[k_part] = {}
      elif not isinstance(curr_items[k_part], collections.Mapping):
        curr_items[k_part] = {'DEFAULT': curr_items[k_part]}
      curr_items = curr_items[k_part]
    last = k_split[-1]
    if last in curr_items:
      curr_items = curr_items[last]
      last = 'DEFAULT'
    if last in curr_items:
      raise ValueError('Failed to map ' + k + ' to ' + v + '. Defined twice?')
    if v == 'NONE':
      v = None
    curr_items[last] = v
  return third_party_map
