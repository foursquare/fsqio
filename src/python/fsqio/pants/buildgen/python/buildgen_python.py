# coding=utf-8
# Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

from __future__ import (
  absolute_import,
  division,
  generators,
  nested_scopes,
  print_function,
  unicode_literals,
  with_statement,
)

from os import path as P

from pants.backend.python.targets.python_binary import PythonBinary
from pants.backend.python.targets.python_library import PythonLibrary
from pants.backend.python.targets.python_tests import PythonTests
from pants.build_graph.address import Address
from pants.util.memo import memoized_property

from fsqio.pants.buildgen.core.buildgen_task import BuildgenTask
from fsqio.pants.buildgen.core.symbol_tree import SymbolTreeNode
from fsqio.pants.buildgen.core.third_party_map_util import check_manually_defined
from fsqio.pants.buildgen.python.python_import_parser import PythonImportParser
from fsqio.pants.buildgen.python.third_party_map_python import (
  get_system_modules,
  python_third_party_map,
)


class BuildgenPython(BuildgenTask):
  @classmethod
  def prepare(cls, options, round_manager):
    super(BuildgenPython, cls).prepare(options, round_manager)
    round_manager.require_data('python')
    round_manager.require_data('python_source_to_exported_symbols')
    round_manager.require_data('source_to_addresses_mapper')

  @classmethod
  def register_options(cls, register):
    register(
      '--first-party-packages',
      default=[],
      advanced=True,
      type=list,
      help="List of python package names produced by the repo (e.g. ['fsqio', 'pants', ...])."
    )
    register(
      '--ignored-prefixes',
      default=[],
      advanced=True,
      type=list,
      help="List of python package names produced by the repo (e.g. ['fsqio', 'pants', ...])."
    )

  @classmethod
  def product_types(cls):
    return [
      'buildgen_python',
    ]

  @property
  def types_operated_on(self):
    return (PythonLibrary, PythonTests)

  @memoized_property
  def first_party_packages(self):
    return self.get_options().first_party_packages

  @memoized_property
  def system_modules(self):
    return get_system_modules(self.first_party_packages)

  _symbol_to_source_tree = None
  @property
  def symbol_to_source_tree(self):
    if self._symbol_to_source_tree is None:
      products = self.context.products
      tree = SymbolTreeNode()
      python_source_to_exported_symbols = products.get_data('python_source_to_exported_symbols')
      for source, symbols in python_source_to_exported_symbols.items():
        for symbol in symbols:
          tree.insert(symbol, source)
      self._symbol_to_source_tree = tree
    return self._symbol_to_source_tree

  _source_to_used_symbols = None
  def source_to_used_symbols(self, source):
    if self._source_to_used_symbols is None:
      self._source_to_used_symbols = {}
    if source not in self._source_to_used_symbols:
      import_linter = PythonImportParser(source, first_party_packages=self.first_party_packages)
      imported_symbols = set()
      _, python_imports = import_linter.lint_and_collect_imports
      for imp in python_imports:
        if imp.package.split('.')[0] in self.first_party_packages:
          if imp.module:
            for alias in imp.aliases:
              imported_symbols.add('.'.join([imp.module, alias[0]]))
          else:
            for alias in imp.aliases:
              imported_symbols.add(alias[0])
      self._source_to_used_symbols[source] = imported_symbols
    return self._source_to_used_symbols[source]

  _source_to_used_third_party_symbols = None
  def source_to_used_third_party_symbols(self, source):
    if self._source_to_used_third_party_symbols is None:
      self._source_to_used_third_party_symbols = {}
    if source not in self._source_to_used_third_party_symbols:
      import_linter = PythonImportParser(source, first_party_packages=self.first_party_packages)
      imported_symbols = set()
      for imp in import_linter.lint_and_collect_imports[1]:
        prefix = imp.package.split('.')[0]
        first_and_builtin_prefixes = frozenset(
          frozenset(self.first_party_packages) |
          frozenset(self.system_modules) |
          frozenset(['__future__'])
        )
        if prefix not in first_and_builtin_prefixes:
          if imp.module:
            for alias in imp.aliases:
              imported_symbols.add('.'.join([imp.module, alias[0]]))
          else:
            for alias in imp.aliases:
              imported_symbols.add(alias[0])
      self._source_to_used_third_party_symbols[source] = imported_symbols
    return self._source_to_used_third_party_symbols[source]

  def buildgen_target(self, target):
    products = self.context.products
    build_graph = self.context.build_graph

    target_used_symbols = set()
    for source_candidate in target.sources_relative_to_buildroot():
      if P.basename(source_candidate) == '__init__.py':
        continue
      if P.splitext(source_candidate)[1] == '.py':
        target_used_symbols.update(self.source_to_used_symbols(source_candidate))

    target_used_sources = set()
    for symbol in target_used_symbols:
      providing_sources = self.symbol_to_source_tree.get(symbol, allow_prefix_imports=True)
      target_used_sources.update(providing_sources)
      if not providing_sources:
        raise Exception(
          'While python buildgenning {}, encountered a symbol with'
          ' no providing target.  This probably means the import moved'
          ' or is misspelled.  It could also mean that there is no BUILD'
          ' target that owns the source that provides the symbol.'
          ' Imported symbol: {}'
          .format(target.address.spec, symbol)
        )

    source_to_addresses_mapper = products.get_data('source_to_addresses_mapper')
    addresses_used_by_target = set()
    for source in target_used_sources:
      for addr in source_to_addresses_mapper.target_addresses_for_source(source):
        used_target = build_graph.get_target(addr)
        if not isinstance(used_target, PythonBinary):
          concrete_address = used_target.concrete_derived_from.address
          addresses_used_by_target.add(concrete_address)

    for source_candidate in target.sources_relative_to_buildroot():
      if P.basename(source_candidate) == '__init__.py':
        continue
      if P.splitext(source_candidate)[1] == '.py':
        target_used_symbols.update(
          self.source_to_used_third_party_symbols(source_candidate)
        )

    # Ignored prefixes are package names that are not managed by buildgen and either represent:
    #   - first party packages
    #   - bad imports for packages that are known to be unrunnable
    #   - imports within pants plugins, which have awkwardly different requirements semantics from normal python code.
    ignored_prefixes = set(self.get_options().ignored_prefixes)
    ignored_prefixes.update(set(self.first_party_packages))

    for symbol in target_used_symbols:
      third_party_dep = check_manually_defined(symbol, subtree=python_third_party_map)
      if third_party_dep:
        addresses_used_by_target.add(Address.parse(third_party_dep))
      elif symbol.split('.')[0] not in ignored_prefixes:
        print(
          'While running python buildgen on {}, encountered a symbol'
          ' without a known providing target.  Symbol: {}'
          ' Ignoring for now...'
          .format(target.address.spec, symbol)
        )

    filtered_addresses_used_by_target = set(
      addr for addr in addresses_used_by_target
      if addr != target.address
    )

    self.adjust_target_build_file(target, filtered_addresses_used_by_target)
