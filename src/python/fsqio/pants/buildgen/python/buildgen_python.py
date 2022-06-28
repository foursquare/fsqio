# coding=utf-8
# Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function

from collections import defaultdict
from hashlib import sha1
import json
import logging
import os
import sysconfig
from textwrap import dedent

from pants.base.build_environment import get_buildroot
from pants.base.exceptions import TaskError
from pants.base.hash_utils import stable_json_hash
from pants.build_graph.address import Address
from pants.util.dirutil import safe_mkdir
from pants.util.memo import memoized_property
from typing import Any, DefaultDict, Dict, List, Set, Text, Tuple

from fsqio.pants.buildgen.core.buildgen_task import BuildgenTask
from fsqio.pants.buildgen.core.symbol_tree import SymbolTreeNode
from fsqio.pants.buildgen.core.third_party_map_util import check_manually_defined
from fsqio.pants.buildgen.python.source_analysis.python_import_parser import PythonImportParser
from fsqio.pants.buildgen.python.third_party_map_python import get_venv_map


logger = logging.getLogger(__name__)

# TODO(mateo): Make these properties of pluggable, per-lang subsystems.
_SOURCE_FILE_TO_ADDRESS_MAP = defaultdict(set)  # type: DefaultDict[str, Set[Text]]
_SYMBOLS_TO_SOURCES_MAP = defaultdict(set)  # type: DefaultDict[str, Set[Text]]


class PythonBuildgenError(TaskError):
  """Indicate an unrecognized Python symbol was imported by a source file."""


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
      '--fatal',
      default=False,
      type=bool,
      help="When True, any imports that cannot be mapped raise and exception. When False, just print a warning."
    )
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
      help="Imported prefixes that buildgen should not attempt to map to a providing target."
    )
    register(
      '--target-alias-blacklist',
      default=[],
      advanced=True,
      type=list,
      help="list target aliases that should not be managed by that task (e.g. ['python_egg', ... ]"
    )
    register(
      '--third-party-map',
      default={},
      advanced=True,
      type=dict,
      help="A mapping of unconventional python imports to their providing python_requirement_library target."
    )
    register(
      '--opt-out-virtualenv-walk',
      default=False,
      advanced=True,
      type=bool,
      help="If this is True, buildgen will not try to map dependencies based on the contents of the virtualenv and"
           "will instead only rely on a third_party_map."
    )
    register(
      '--additional-virtualenv-roots',
      default=[],
      advanced=True,
      type=list,
      help="Pass a list of virtualenv roots for buildgen. As long as not opted-out, buildgen will walk these dirs to"
           "map imports. If none are passed then buildgen will default to walking just the pants virtualenv."
    )
    register(
      '--force-third-party',
      default=[],
      advanced=True,
      type=list,
      help="Force buildgen to considered these packages as 3rd party and not system packages."
    )

  @classmethod
  def product_types(cls):
    # type: () -> List[Text]
    return [
      'buildgen_python',
    ]

  @classmethod
  def implementation_version(cls):
    return super(BuildgenPython, cls).implementation_version() + [('BuildgenPython', 2)]

  @property
  def cache_target_dirs(self):
    # type: () -> bool
    return True

  @memoized_property
  def supported_target_aliases(self):
    # type: () -> Tuple[Text, Text]
    return ('python_library', 'python_tests')

  @memoized_property
  def third_party_target_aliases(self):
    # type: () -> List[Text]
    return ['python_requirement_library']

  @memoized_property
  def target_alias_blacklist(self):
    return self.get_options().target_alias_blacklist

  @memoized_property
  def first_party_packages(self):
    return tuple(self.get_options().first_party_packages)

  @memoized_property
  def ignored_prefixes(self):
    return tuple(self.get_options().ignored_prefixes + ['__future__'])

  @memoized_property
  def map_python_deps(self):
    # type: () -> Tuple[Set[str], Dict[str, Any]]
    address_mapper = self.context.address_mapper
    source_dirs = self.get_options().third_party_dirs
    module_list = {}  # type: Dict[str, Any]
    reqs = set()  # type: Set[str]
    for source_dir in source_dirs:
      # TODO(mateo): Before Pants 1.7.0, this was able to derive the addressable without hydrating the target.
      for address in address_mapper.scan_addresses(os.path.join(get_buildroot(), source_dir)):
        target = self.context.build_graph.resolve_address(address)
        if target.type_alias in self.third_party_target_aliases:
          module_list[target.name.replace('-', '_')] = address.spec
        # This gathers a list of the python requirements as strings. Forget you saw this.
        reqs.update([str(r.requirement) for r in target.requirements])
    return reqs, module_list

  @memoized_property
  def python_virtual_envs(self):
    # type: () -> List[str]
    """Return the standard_lib directory of any configured virtualenv."""
    # Additional venvs can be passed as options but this falls back to using the pants virtualenv.
    pantsenv = sysconfig.get_path("purelib") or ""  # HACK(jeffreyc): thanks, mypy.
    virtualenvs = [pantsenv]
    platlib = sysconfig.get_path("platlib") or ""  # HACK(jeffreyc): thanks, mypy.
    if pantsenv != platlib:
      virtualenvs.append(platlib)
    user_envs = self.get_options().additional_virtualenv_roots
    for user_env in user_envs:
      # HACK(jeffreyc): append `site-packages` for API compatibility.
      virtualenvs.append(os.path.join(user_env, "site-packages"))
    return [ve for ve in virtualenvs if ve]

  @memoized_property
  def venv_modules(self):
    # The deps are python_requirement_library specs, the reqs are the requirements.txt entries.
    reqs, deps = self.map_python_deps
    analysis_hash = self.analysis_hash(reqs)
    analysis_file = os.path.join(self.workdir, 'python-analysis-{}.json'.format(analysis_hash))
    if not os.path.isfile(analysis_file):
      mapping = get_venv_map(self.python_virtual_envs, deps)
      with open(analysis_file, 'wb') as f:
        json.dump(mapping, f)
    else:
      with open(analysis_file, 'rb') as f:
        # This is an escape hatch in case the json cannot be read. Maybe it should just raise an exception.
        try:
          mapping = json.load(f)
        except Exception:
          logger.debug("Could not read the buildgen analysis file, regenerating: {}.".format(f))
          mapping = get_venv_map(self.python_virtual_envs, deps)
          os.remove(analysis_file)

    for override in self.get_options().force_third_party:
      if override in mapping['python_modules']:
        mapping['python_modules'].remove(override)

    return mapping

  @memoized_property
  def system_modules(self):
    return set(self.venv_modules['python_modules'])

  @memoized_property
  def symbol_to_target_map(self):
    return self.venv_modules['third_party']

  @memoized_property
  def symbol_to_source_tree(self):
    # type: () -> SymbolTreeNode
    tree = SymbolTreeNode()
    python_source_to_exported_symbols = self.context.products.get_data('python_source_to_exported_symbols')
    for source, symbols in python_source_to_exported_symbols.items():
      for symbol in symbols:
        tree.insert(symbol, source)
    self._symbol_to_source_tree = tree
    return self._symbol_to_source_tree

  @memoized_property
  def opt_out_virtualenv_walk(self):
    return self.get_options().opt_out_virtualenv_walk

  def analysis_hash(self, reqs):
    hasher = sha1()
    reqs_hash = stable_json_hash([sorted(list(reqs))])
    hasher.update(reqs_hash)

    # Add virtualenv roots to the hash. Analysis should be redone if pointed at a new venv, even if all else the same.
    for venv in self.python_virtual_envs:
      hasher.update(venv)

    # Invalidate if pants changes.
    hasher.update(self.get_options().pants_version)
    hasher.update(self.get_options().cache_key_gen_version)

    # Invalidate the cache if the task version is bumped.
    hasher.update(str(self.implementation_version()))
    return hasher.hexdigest()

  _source_to_symbols_map = defaultdict(set)  # type: DefaultDict[str, Set[Text]]

  def get_used_symbols(self, source):
    # type: (str) -> Set[Text]
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
    safe_mkdir(self.workdir)
    source_files = [f for f in target.sources_relative_to_buildroot() if f.endswith('.py')]
    build_graph = self.context.build_graph
    source_to_addresses_mapper = self.context.products.get_data('source_to_addresses_mapper')

    # Gather symbols imported from first party source files.
    target_used_symbols = set()
    addresses_used_by_target = set()
    for source_candidate in source_files:
      try:
        used_symbols = self.get_used_symbols(source_candidate)
      except Exception as e:
        raise Exception(
          dedent(
            """
            {}\n
            Source file: {}
            Referenced by: {}
            """.format(e, source_candidate, target.address.spec)
          )
        )
      target_used_symbols.update(used_symbols)
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

      # Since the symbol is not first party, it needs to be mapped to a target.
      else:
        # The twitter.commons/apache.aurora packages are hopeless to comprehensively map. They have a number of issues:
        #   * They share common `top_level.txt` and 'namespace_packages.txt' values
        #   * Heuristics based off the package name are invalid
        #         * apache.aurora.thrift == gen.apache.aurora.[modules].{1.py, 2.py, 3.py}
        #         * apache.aurora.thermos == gen.apache.thermos.{a.py, b.py, c.py}
        #           * how to tell that gen.apache.thermos is not a module of gen.apache.aurora?
        #   * Consequently:
        #       * they clobber each other's namespace
        #       * There is no programmatic way to tell between modules
        #
        # The valid imports are trivial to determine but there is no deterministic way to map that to the package name.
        # Without these, we could entirely rely on the virtualenv introspection but instead third_party_map lives.

        if not self.opt_out_virtualenv_walk:
          import_map = self.symbol_to_target_map
          if prefix not in import_map:
            # Slice the import ever shorter until we either match to a known import or run out of parts.
            prefix = symbol
            parts = len(prefix.split('.'))
            while prefix not in import_map and parts > 1:
              prefix, _ = prefix.rsplit('.', 1)
              parts -= 1
        else:
          import_map = {}

        # Both of these return a target spec string if there is a match and None otherwise.
        dep = check_manually_defined(symbol, self.get_options().third_party_map) or import_map.get(prefix)
        if not dep:
          msg = dedent(
            """\
            While running python buildgen, a symbol was found without a known providing target.
            Target: {}
            Symbol: {}
            """.format(target.address.spec, symbol)
          )
          # TODO(mateo): Make this exception fail-slow. Better to gather all bg failures and print at end.
          if self.get_options().fatal:
            raise PythonBuildgenError(msg)
          else:
            print('{}Ignoring for now since fatal errors are off'.format(msg))
        else:
          addresses_used_by_target.add(Address.parse(dep))

    # Remove any imports from within the same module.
    filtered_addresses_used_by_target = {
      addr for addr in addresses_used_by_target
      if addr != target.address
    }
    self.adjust_target_build_file(target, filtered_addresses_used_by_target)
