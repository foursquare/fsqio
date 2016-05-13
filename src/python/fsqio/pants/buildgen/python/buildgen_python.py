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

from collections import defaultdict
import os

from pants.backend.python.targets.python_library import PythonLibrary
from pants.backend.python.targets.python_tests import PythonTests
from pants.base.build_environment import get_buildroot
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


_SOURCE_FILE_TO_ADDRESS_MAP = defaultdict(set)
_SYMBOLS_TO_SOURCES_MAP = defaultdict(set)

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
      '--third-party-dirs',
      default=['3rdparty/python'],
      advanced=True,
      type=list,
      help="List of source roots that hold 3rdparty python BUILD file definitions."
    )
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
    register(
      '--target-alias-blacklist',
      default=[],
      advanced=True,
      type=list,
      help="list target aliases that should not be managed by that task (e.g. ['python_egg', ... ]"
    )
    register(
      '--additional-third-party-map',
      default={},
      advanced=True,
      type=dict,
      # See the test_third_party_map_util.py for examples.
      help="A dict that defines additional third party mappings (may be nested). See third_party_map_python.py "
        "for defaults. Mappings passed to this option will take precedence over the defaults."
    )

  @classmethod
  def product_types(cls):
    return [
      'buildgen_python',
    ]

  @memoized_property
  def types_operated_on(self):
    return (PythonLibrary, PythonTests)

  @memoized_property
  def third_party_target_aliases(self):
    return ['python_requirement_library']

  @memoized_property
  def target_alias_blacklist(self):
    return self.get_options().target_alias_blacklist

  @memoized_property
  def first_party_packages(self):
    return tuple(self.get_options().first_party_packages)

  @memoized_property
  def system_modules(self):
    return frozenset(get_system_modules())

  @memoized_property
  def ignored_prefixes(self):
    return tuple(self.get_options().ignored_prefixes + ['__future__'])

  @memoized_property
  def third_party_map(self):
    return python_third_party_map

  @memoized_property
  def third_party_deps_addresses(self):
    name_to_address_map = defaultdict(lambda: None)
    address_mapper = self.context.address_mapper
    source_dirs = self.get_options().third_party_dirs

    for source_dir in source_dirs:
      for address in address_mapper.scan_addresses(os.path.join(get_buildroot(), source_dir)):
        _, addressable = address_mapper.resolve(address)
        # TODO(mateo): Break this into a property of BuildgenTask that returns a tuple of accepted dependency types.
        if addressable.addressed_alias in self.third_party_target_aliases:
          # Use light heuristics here: transform it to a valid identifier and convert to lowercase.
          dep_name = addressable.addressed_name.lower().replace('-', '_')
          name_to_address_map[dep_name] = address.spec

    return name_to_address_map

  @memoized_property
  def symbol_to_source_tree(self):
    tree = SymbolTreeNode()
    python_source_to_exported_symbols = self.context.products.get_data('python_source_to_exported_symbols')
    for source, symbols in python_source_to_exported_symbols.items():
      for symbol in symbols:
        tree.insert(symbol, source)
    self._symbol_to_source_tree = tree
    return self._symbol_to_source_tree

  _source_to_symbols_map = defaultdict(set)
  def get_used_symbols(self, source):
    if source not in self._source_to_symbols_map:
      import_linter = PythonImportParser(source, self.first_party_packages)
      imported_symbols = set()
      _, python_imports = import_linter.lint_and_collect_imports
      for imp in python_imports:
        prefix = imp.package.split('.')[0]
        if prefix not in self.ignored_prefixes and prefix not in self.system_modules:
          if imp.module:
            for alias in imp.aliases:
              imported_symbols.add('.'.join([imp.module, alias[0]]))
          else:
            for alias in imp.aliases:
              imported_symbols.add(alias[0])
        self._source_to_symbols_map[source] = imported_symbols
    return self._source_to_symbols_map[source]

  def buildgen_target(self, target):
    source_files = [f for f in target.sources_relative_to_buildroot() if f.endswith('.py')]
    build_graph = self.context.build_graph
    address_mapper = self.context.address_mapper
    source_to_addresses_mapper = self.context.products.get_data('source_to_addresses_mapper')

    # Gather symbols imported from first party source files.
    target_used_symbols = set()
    addresses_used_by_target = set()
    for source_candidate in source_files:
      target_used_symbols.update(self.get_used_symbols(source_candidate))

    for symbol in target_used_symbols:
      prefix = symbol.split('.')[0]

      # If the symbol is a first party package, map it to providing source files and memoize the relation.
      if prefix in self.first_party_packages:
        if symbol not in _SYMBOLS_TO_SOURCES_MAP:
          providing_sources = self.symbol_to_source_tree.get(symbol, allow_prefix_imports=True)
          if not providing_sources:
            raise Exception(
              'While python buildgenning {}, encountered a symbol with'
              ' no providing target.  This probably means the import moved'
              ' or is misspelled.  It could also mean that there is no BUILD'
              ' target that owns the source that provides the symbol.'
              ' Imported symbol: {}'
              .format(target.address.spec, symbol)
            )
          _SYMBOLS_TO_SOURCES_MAP[symbol] = providing_sources

        # Map source file to concrete addresses, tracing codegen back to its concrete target, and cache it.
        for source in _SYMBOLS_TO_SOURCES_MAP[symbol]:
          if source not in _SOURCE_FILE_TO_ADDRESS_MAP:
            target_addresses = set()
            for address in source_to_addresses_mapper.target_addresses_for_source(source):
              concrete = build_graph.get_concrete_derived_from(address)
              if concrete.type_alias != 'python_binary':
                target_addresses.add(concrete.address)
            _SOURCE_FILE_TO_ADDRESS_MAP[source].update(target_addresses)
          addresses_used_by_target.update(_SOURCE_FILE_TO_ADDRESS_MAP[source])

      # Since the symbol is not first party, it needs to be mapped to a target or ignored.
      else:
        # Both of these return None if there is no match.
        third_party_dep = self.third_party_deps_addresses[prefix] or check_manually_defined(symbol, self.merged_map)
        if third_party_dep:
          addresses_used_by_target.add(Address.parse(third_party_dep))
        else:
          # TODO(mateo): make fatal an option.
          print(
            'While running python buildgen on {}, encountered a symbol'
            ' without a known providing target.  Symbol: {}'
            ' Ignoring for now...'
            .format(target.address.spec, symbol)
          )

    # Remove any imports from within the same module.
    filtered_addresses_used_by_target = set([
      addr for addr in addresses_used_by_target
      if addr != target.address
    ])
    self.adjust_target_build_file(target, filtered_addresses_used_by_target)
