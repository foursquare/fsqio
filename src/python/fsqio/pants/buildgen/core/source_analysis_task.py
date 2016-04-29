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

from itertools import chain
import json
import os
from os import path as P

from pants.base.build_environment import get_buildroot
from pants.task.task import Task


class SourceAnalysisTask(Task):
  """A parent Task for buildgen upstream analysis tasks which provide source -> analysis products.

  SourceAnalysisTask factors out a large amount of boilerplate and handles caching logic.
  """

  @classmethod
  def product_types(cls):
    return [
      cls.analysis_product_name(),
    ]

  @classmethod
  def analysis_product_name(cls):
    """A string that names the product type this task produces."""
    raise NotImplementedError()

  @property
  def claimed_target_types(self):
    """A tuple of target types that the implementing class will do source analysis on."""
    raise NotImplementedError()

  def is_analyzable(self, source_relpath):
    """Whether or not a source is a candidate for analysis.

    Generally this will just be a test of the source's extension.
    """
    raise NotImplementedError()

  def analyze_sources(self, source_relpaths):
    """Called to analyze uncached sources.

    Returns a map from source relpaths to opaque analysis objects.
    It is an error f the returned map does not have a key for every passed in source relpath.
    """
    raise NotImplementedError()

  def __init__(self, *args, **kwargs):
    super(SourceAnalysisTask, self).__init__(*args, **kwargs)

  def execute(self):
    products = self.context.products
    targets = [t for t in self.context.target_roots if isinstance(t, self.claimed_target_types)]

    def vt_analysis_file(vt):
      return P.join(self._workdir, vt.target.id, vt.cache_key.hash, 'analysis.json')

    with self.invalidated(targets, invalidate_dependents=False) as invalidation_check:
      analyzable_sources_by_target = {
        vt.target: set(
          source for source in vt.target.sources_relative_to_buildroot()
          if self.is_analyzable(source)
        )
        for vt in invalidation_check.all_vts
      }
      invalid_targets = [vt.target for vt in invalidation_check.invalid_vts]
      invalid_analyzable_sources = set(
        chain.from_iterable(
          [source for source in t.sources_relative_to_buildroot() if self.is_analyzable(source)]
          for t in invalid_targets
        )
      )
      calculated_analysis = self.analyze_sources(list(invalid_analyzable_sources))
      uncalculated_analysis = invalid_analyzable_sources - set(calculated_analysis.keys())
      if uncalculated_analysis:
        raise Exception(
          '{0} failed to calculate analysis for the following sources:\n *{1}\n'
          .format(
            type(self).__name__,
            '\n *'.join(sorted(uncalculated_analysis))
          )
        )

      vts_artifactfiles_pairs = []
      for vt in invalidation_check.invalid_vts:
        target_analysis = {}
        for source in analyzable_sources_by_target[vt.target]:
          target_analysis[source] = calculated_analysis[source]
        target_analysis_bytes = json.dumps(target_analysis)
        target_analysis_path = vt_analysis_file(vt)
        target_analysis_abs_dir = P.dirname(P.join(get_buildroot(), target_analysis_path))
        if not P.isdir(target_analysis_abs_dir):
          os.makedirs(target_analysis_abs_dir)
        with open(P.join(get_buildroot(), target_analysis_path), 'wb') as f:
          f.write(target_analysis_bytes)
        vts_artifactfiles_pairs.append((vt, [target_analysis_path]))

      analysis_product = {}
      analysis_product.update(calculated_analysis)

      for vt in invalidation_check.all_vts:
        if vt.valid:
          target_analysis_path = vt_analysis_file(vt)
          with open(P.join(get_buildroot(), target_analysis_path), 'rb') as f:
            analysis_bytes = f.read()
          target_analysis = json.loads(analysis_bytes)
          analysis_product.update(target_analysis)

      if self.artifact_cache_writes_enabled():
        self.update_artifact_cache(vts_artifactfiles_pairs)
      products.safe_create_data(self.analysis_product_name(), lambda: analysis_product)
