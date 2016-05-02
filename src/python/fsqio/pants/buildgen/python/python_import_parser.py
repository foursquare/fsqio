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

import ast
from collections import defaultdict
import tokenize


class cached_property(object):
  """From https://github.com/pydanny/cached-property/blob/master/cached_property.py

  A property that is only computed once per instance and then replaces itself
  with an ordinary attribute. Deleting the attribute resets the property.
  Source: https://github.com/bottlepy/bottle/commit/fa7733e075da0d790d809aa3d2f53071897e6f76
  """   # noqa
  def __init__(self, func):
    self.__doc__ = getattr(func, '__doc__')
    self.func = func

  def __get__(self, obj, cls):
    if obj is None:
        return self
    value = obj.__dict__[self.func.__name__] = self.func(obj)
    return value


class ParsedImport(object):

  def __init__(self, module=None, aliases=(), comments=()):
    self.module = module
    self.aliases = tuple(sorted(aliases, key=lambda p: p[0]))
    self.comments = comments

  @property
  def package(self):
    return self.module or self.aliases[0][0]

  def __bool__(self):
    return len(self.aliases) > 0

  def __nonzero__(self):
    return self.__bool__()

  def __repr__(self):
    return self.render()


class PythonImportParser(object):

  def __init__(self, source_path, first_party_packages):
    self._first_party_packages = first_party_packages
    self._source_path = source_path

  @cached_property
  def source_code(self):
    try:
      with open(self._source_path, 'rb') as f:
        return f.read().decode('utf-8')
    except Exception as e:
      raise Exception('Error opening source: {}\n{}'.format(self._source_path, e))

  @cached_property
  def source_lines(self):
    return self.source_code.split('\n')

  @cached_property
  def tokens(self):
     with open(self._source_path, 'rb') as f:
       return list(tokenize.generate_tokens(f.readline))

  @cached_property
  def tree(self):
    try:
      return ast.parse(self.source_code.encode('utf-8'), mode='exec')
    except Exception as e:
      raise Exception('Failed to parse python source: {}\n{}'.format(self._source_path, e))

  @cached_property
  def first_non_import_index(self):
    for node in self.tree.body:
      is_import = isinstance(node, (ast.Import, ast.ImportFrom))
      is_docstring = isinstance(node, ast.Expr) and isinstance(node.value, ast.Str)
      is_author_decl = (
        isinstance(node, ast.Assign) and
        isinstance(node.targets[0], ast.Name) and
        node.targets[0].id == '__author__'
      )
      if not (is_import or is_docstring or is_author_decl):
        # When indexing into our list of strings, we are 0-indexed, but lineno is 1-indexed.
        real_code_index = node.lineno - 1
        # Now we walk the line back as long as we see comments
        def is_comment(index):
          line = self.source_lines[index].strip()
          return line.startswith('#')
        while real_code_index > 0 and is_comment(real_code_index - 1):
          real_code_index -= 1
        return real_code_index
    return 0

  @cached_property
  def index_to_tokens(self):
    index_to_tokens_map = defaultdict(list)
    for token in self.tokens:
      tok_type, tok_string, (srow, scol), (erow, ecol), line = token
      index_to_tokens_map[srow - 1].append(token)
    return index_to_tokens_map

  def corrected_lineno(self, node):
    # For some reason, bare docstrings report their lineno as the last line of the
    # string, instead of the first (unlike all other nodes that I've encountered).
    if isinstance(node, ast.Expr) and isinstance(node.value, ast.Str):
      return node.value.lineno - len(node.value.s.split('\n')) + 1
    else:
      return node.lineno

  def tokens_in_node_at_index(self, tree_index):
    node = self.tree.body[tree_index]
    if len(self.tree.body) > tree_index + 1:
      next_node = self.tree.body[tree_index + 1]
      first_index_after_node = self.corrected_lineno(next_node) - 1
    else:
      first_index_after_node = len(self.source_lines)
    # Now we collect any comments or whitespace directly above the next node.
    while first_index_after_node > node.lineno - 1:
      line = self.source_lines[first_index_after_node - 1].strip()
      if line and not line.startswith('#'):
        # We've hit something not a comment, so we're done.
        break
      # Walk back a line.
      first_index_after_node -= 1
    for i in range(node.lineno - 1, first_index_after_node):
      for token in self.index_to_tokens.get(i, []):
        yield token

  @cached_property
  def lint_and_collect_imports(self):
    module_to_aliases = defaultdict(set)
    module_to_comments = defaultdict(list)
    bare_imports = set()
    errors = []
    for i, node in enumerate(self.tree.body):
      node_comments = []
      if isinstance(node, (ast.Import, ast.ImportFrom)):
        if node.lineno > self.first_non_import_index:
          errors.append((
            'Imports at global scope should only occur at the top of the file.',
            node.lineno,
          ))

        for token in self.tokens_in_node_at_index(i):
          if token[0] == tokenize.COMMENT:
            errors.append((
              'Inline comments on imports are forbidden, as the automatic import linter cannot'
              ' detect them and will silently stomp them.  If you feel strongly that you need'
              ' to comment an import, put it directly before the "from ..." or "import ..."'
              ' line in question, with no extra newlines on either side.',
              token[2][0],
              node.lineno,
            ))
        # Now we collect any comments directly above this node.
        real_code_index = node.lineno - 1
        while real_code_index > 0:
          line = self.source_lines[real_code_index - 1].strip()
          if not line.startswith('#'):
            # We've hit something not a comment, so we're done.
            break
          # This is a real comment.  Prepend it to the accumulated comments
          # since we're walking backwards.
          node_comments.insert(0, line)
          # Walk back a line.
          real_code_index -= 1

      if isinstance(node, ast.Import):
        if len(node.names) > 1:
          errors.append((
            'All "import ..." lines should import exactly one thing.',
            node.lineno,
          ))
        else:
          alias = node.names[0]
          bare_imports.add(
            ParsedImport(
              module=None,
              aliases=((alias.name, alias.asname),),
              comments=tuple(node_comments),
            )
          )
      elif isinstance(node, ast.ImportFrom):
        if node.level != 0:
          errors.append((
            'Relative imports are forbidden.  Always use fully qualified package names'
            ' when importing.',
            node.lineno,
          ))
        for alias in node.names:
          if alias.name == '*':
            errors.append((
              'Wildcard imports are forbidden.  Only import the symbols you need, or import'
              ' the top level package and use attribute access on it.',
              node.lineno,
            ))
          module_to_aliases[node.module].add((alias.name, alias.asname))
        module_to_comments[node.module].extend(node_comments)
    imports = list(bare_imports)
    for module, aliases in module_to_aliases.items():
      comments = module_to_comments.get(module)
      imports.append(ParsedImport(module=module, aliases=tuple(aliases), comments=tuple(comments)))
    return errors, imports
