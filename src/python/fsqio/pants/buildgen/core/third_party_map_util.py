# coding=utf-8
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import


def merge_map(a, b):
  """Recursively merge two dictionaries, with any given subtree in b recursively taking priority over a."""
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
