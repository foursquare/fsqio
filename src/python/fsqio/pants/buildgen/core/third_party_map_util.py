# coding=utf-8
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import


class Default(object):
  """A sentinel key.

  Default indicates that if subtrees don't match the rest of the symbol, the value mapped by
  Default should be used.
  """


class Skip(object):
  """A sentinel value.

  If a symbol maps to Skip, don't map it to anything, but don't consider it an error for the
  symbol to be unmapped.
  """


def check_manually_defined(symbol, subtree=None):
  """Checks to see if a symbol was hand mapped by third_party_map."""

  if subtree == Skip:
    return Skip
  if isinstance(subtree, basestring) or subtree is None:
    return subtree

  parts = symbol.split('.')
  if not parts:
    raise ValueError('Came to the end of the symbol while still traversing tree.  Subtree '
                     'keys were {keys}'.format(keys=subtree.keys()))
  if parts[0] not in subtree:
    if Default in subtree:
      return subtree[Default]
    else:
      return None
  else:
    return check_manually_defined('.'.join(parts[1:]), subtree[parts[0]])
