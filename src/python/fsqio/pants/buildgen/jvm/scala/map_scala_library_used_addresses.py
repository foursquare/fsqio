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
from itertools import chain
import sys

from pants.backend.jvm.targets.java_tests import JavaTests
from pants.backend.jvm.targets.scala_library import ScalaLibrary
from pants.base.exceptions import TaskError
from pants.build_graph.address import Address
from pants.util.memo import memoized_property

from fsqio.pants.buildgen.core.buildgen_base import BuildgenBase
from fsqio.pants.buildgen.core.third_party_map_util import Skip, check_manually_defined
from fsqio.pants.buildgen.jvm.third_party_map_jvm import jvm_third_party_map


class UsedSymbolException(TaskError):
  """Indicate a symbol was found that has no corresponding target."""


class MapScalaLibraryUsedAddresses(BuildgenBase):
  """Consults the analysis products to map the addresses that all ScalaLibrary targets use.

  This includes synthetic targets that are the result of codegen.
  """

  @classmethod
  def product_types(cls):
    return [
      'scala_library_to_used_addresses',
    ]

  @classmethod
  def prepare(cls, options, round_manager):
    round_manager.require_data('scala_source_to_exported_symbols')
    round_manager.require_data('scala_source_to_used_symbols')
    round_manager.require_data('source_to_addresses_mapper')
    round_manager.require_data('jvm_symbol_to_source_tree')
    round_manager.require_data('scala')
    round_manager.require_data('java')

  def _symbols_used_by_scala_target(self, target):
    """Consults the analysis products and returns a set of all symbols used by a scala target."""
    products = self.context.products
    source_symbols_used_products = products.get_data('scala_source_to_used_symbols')
    used_symbols = set()
    for source in target.sources_relative_to_buildroot():
      if source in source_symbols_used_products:
        analysis = source_symbols_used_products[source]
        used_symbols.update(analysis['imported_symbols'])
        used_symbols.update(analysis['fully_qualified_names'])
    return used_symbols

  @property
  def _internal_symbol_tree(self):
    return self.context.products.get_data('jvm_symbol_to_source_tree')

  @property
  def _source_mapper(self):
    return self.context.products.get_data('source_to_addresses_mapper')

  def _is_test(self, target):
    """A little hack to determine if an address lives in one of the testing directories."""
    return target.concrete_derived_from.address.spec_path.startswith(self.test_dirs)

  @memoized_property
  def test_dirs(self):
    return tuple(self.buildgen_subsystem.test_dirs)

  def _manually_defined_spec_to_address(self, spec):
    if '/' in spec:
      return Address.parse(spec)
    else:
      # TODO(mateo): Probably okay to assume 3rdparty at this point. But could be moved into config.
      return Address.parse('3rdparty:{0}'.format(spec))

  def _scala_library_used_addresses(self, target):
    """Consults the analysis products to construct a set of addresses a scala library uses."""
    syms = self._symbols_used_by_scala_target(target)
    used_addresses = set()
    errors = []
    for symbol in syms:
      exact_matching_sources = self._internal_symbol_tree.get(symbol, exact=False)
      manually_defined_target = check_manually_defined(symbol, subtree=jvm_third_party_map)
      if manually_defined_target and exact_matching_sources:
        print(
          'ERROR: buildgen found both sources and manually defined in third_party_map_jvm'
          ' targets for this symbol.\n'
          'Target: {0}\n'
          'Jvm symbol used by target: {1}\n'
          'Manually defined target for symbol: {3}\n'
          'Sources found defining symbol: \n{2}\n'
          .format(
            target.address.reference(),
            symbol,
            '\n'.join('  * {0}'.format(source) for source in exact_matching_sources),
            self._manually_defined_spec_to_address(manually_defined_target).reference(),
          )
        )
        errors.append((target.address.reference(), symbol))
        continue
      elif exact_matching_sources:
        addresses = set(chain.from_iterable(
          self._source_mapper.target_addresses_for_source(source)
          for source in exact_matching_sources
        ))
      elif manually_defined_target == Skip:
        continue
      elif manually_defined_target:
        addresses = [self._manually_defined_spec_to_address(manually_defined_target)]
      else:
        errors.append((target.address.reference(), symbol))
        continue
      for address in addresses:
        dep = self.context.build_graph.get_target(address)

        # NOTE(mateo): This cannot happen when using pom-resolve, but OSS consumers have been bitten when dep is None.
        # I was unable to repro using Ivy, but this check is cheap enough to be worth it no matter the resolver.
        if not dep:
          raise UsedSymbolException("An address was used that was not injected into the build graph! Make sure that "
            "there is a matching BUILD definition for this used address: {}".format(address))
        if address == target.address:
          pass
        elif self._is_test(dep) and not self._is_test(target):
          pass
        else:
          # In the end, we always actually depend on concrete targets.  But for now we preserve
          # the information that this dependency (could have been) synthetic, and let downstream
          # consumers normalize this to a concrete target if necessary.
          used_addresses.add(dep.address)

    if errors:
      print('ERROR: Failed to map these symbols used by the following target to a providing'
            ' target:', file=sys.stderr)
      err_msg = []
      for spec, symbol in errors:
        err_msg.append("")
        err_msg.append("Symbol:" + symbol)
        err_msg.append("Target:" + spec)
      err_msg.append('Failed to map scala libraries to used symbols.  See error output above.')
      raise Exception('\n'.join(err_msg))
    return used_addresses

  def execute(self):
    products = self.context.products
    scala_library_to_used_addresses = defaultdict(set)
    def is_scala_lib(t):
      return isinstance(t, (ScalaLibrary, JavaTests))
    for target in self.context.build_graph.targets(is_scala_lib):
      scala_library_to_used_addresses[target].update(self._scala_library_used_addresses(target))
    products.safe_create_data('scala_library_to_used_addresses',
                              lambda: scala_library_to_used_addresses)
