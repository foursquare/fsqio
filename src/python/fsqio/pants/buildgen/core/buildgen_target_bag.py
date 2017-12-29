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

import re

from pants.build_graph.resources import Resources


class BuildgenTargetBag(Resources):
  """Aggregate all targets under a source tree directory that match the configured target type.

  BuildgenTargetBags targets have their dependencies managed by buildgen. Each target minimally defines a
  source directory and a target_alias type (e.g. 'python_library'). Buildgen will add a dependency to the implementing
  buildgen_target_bag BUILD definition for every matching target under that source dir.
  """
  # NOTE(mateo): BuildgenTargetBag is a Resources subclass to get past type checks in the Python backend and potentially
  # similar checks in the future, ie `isinstance(target, PythonTarget)`. These checks generally special case
  # Resource targets as passthrough, and we use that property here.
  # https://github.com/pantsbuild/pants/issues/4938
  #
  # Potentially we could use the expanded export system to properly declare the targets freighted by these bags. TBD

  def __init__(self,
               target_type_alias=None,
               source_tree=None,
               additional_generated_targets=None,
               ignored_targets_regex=None,
               *args,
               **kwargs):
    """
    :param str target_type_alias binary: The target_alias to gather into aggregated bag.
        (e.g. python_library or scala_library).
    :param str source_tree: Path relative to the buildroot - buildgen will gather the aggregated target_aliases
        from this dir.
    :param str additional_generated_targets: Any additional targets of the same target_alias that should be aggreagated
         but may not be under the same source_tree.
    :param str ignored_targets_regex: A regular expression capable of being compiled by the python 're' module.
        Targets whose address matches the regex will not be aggregated into the bag.
    """
    self.target_type_alias = target_type_alias
    self.source_tree = source_tree
    self.additional_generated_targets = additional_generated_targets
    self.ignored_targets_regex = re.compile(ignored_targets_regex) if ignored_targets_regex else None
    super(BuildgenTargetBag, self).__init__(*args, **kwargs)
