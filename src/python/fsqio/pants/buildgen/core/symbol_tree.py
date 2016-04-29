# coding=utf-8
# Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import

from itertools import chain


class SymbolTreeNode(object):
  """A simple prefix tree with some custom logic for working with JVM symbols.

  Also used with Python symbols.
  """

  def __init__(self, children=None, values=None):
    self.children = children or {}
    self.values = values or set()

  def _insert(self, symbol_list, value):
    if not symbol_list:
      self.values.add(value)
    else:
      head, rest = symbol_list[0], symbol_list[1:]
      if head not in self.children:
        self.children[head] = SymbolTreeNode()
      child_node = self.children[head]
      child_node._insert(rest, value)

  def insert(self, symbol, value):
    """Insert value into the tree at symbol.

    :param string symbol: This symbol will be split on '.' to create nodes in the prefix tree.
    :param string value: This value (generally a source path) will be inserted into the set of
      values provided at the node produced by `symbol`.
    """
    return self._insert(symbol.split('.'), value)

  def flattened_subtree(self):
    """Returns the set of all values provided by this node and all of its descendents."""
    return set(chain(self.values,
                     *[child.flattened_subtree() for child in self.children.values()]))

  def _get(self, symbol_list, allow_prefix_imports, exact):
    if not symbol_list:
      if not self.values and allow_prefix_imports:
        return self.flattened_subtree()
      else:
        return set(self.values)
    elif symbol_list == ['_']:
      return self.flattened_subtree()
    else:
      head, rest = symbol_list[0], symbol_list[1:]
      if head in self.children:
        return self.children[head]._get(rest, allow_prefix_imports, exact)
      elif exact:
        return set()
      else:
        return set(self.values)

  def get(self, symbol, allow_prefix_imports=False, exact=False):
    """Returns the values at the node inferred from `symbol`.

    :param string symbol: This symbol will be split on '.' to traverse nodes in the prefix tree.
    :param bool allow_prefix_imports: If the symbol refers to a node which does not have any
      values associated with it (it's internal, and therefore has children), instead of returning
      nothing, return all of the values provided by its children (flattened_subtree).
    :param bool exact: Only return the values at a node if it is an exact match for the symbol.
      If a prefix node has values but does not have children to match the rest of the symbol,
      it will return an empty set.  Note that `allow_prefix_imports` and `exact` are mutually
      exclusive, and passing them both will cause an exception to be raised.
    """
    if allow_prefix_imports and exact:
      raise ValueError('Cannot call SymbolTreeNode.get() with both allow_prefix_imports=True and'
                       ' exact=True.  Symbole was: {0}'.format(symbol))
    return self._get(
      symbol.split('.'),
      allow_prefix_imports=allow_prefix_imports,
      exact=exact,
    )
