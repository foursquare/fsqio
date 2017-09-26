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

from pants.task.task import Task


class SourceAnalysisTask(Task):
  """A parent Task for buildgen upstream analysis tasks which provide source -> analysis products.

  SourceAnalysisTask factors out a large amount of boilerplate and handles caching logic.
  """

  @classmethod
  def implementation_version(cls):
    return super(SourceAnalysisTask, cls).implementation_version() + [('SourceAnalysisTask', 3)]

  @classmethod
  def product_types(cls):
    return [
      cls.analysis_product_name(),
    ]

  @property
  def cache_target_dirs(self):
    return True

  @classmethod
  def analysis_product_name(cls):
    """A string that names the product type this task produces."""
    raise NotImplementedError()

  @property
  def claimed_target_types(self):
    """A tuple of target types that the implementing class will do source analysis on."""
    raise NotImplementedError()

  def targets(self):
    """Returns targets to analyze. Override this to look at more than just roots."""
    return filter(lambda t: isinstance(t, self.claimed_target_types), self.context.target_roots)

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
    with self.invalidated(self.targets(), invalidate_dependents=False) as invalidation_check:
      def target_analysis_file(vt):
        return os.path.join(vt.results_dir, 'analysis.json')

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
          '{0} failed to calculate analysis for the following sources:\n * {1}\n'
          .format(
            type(self).__name__,
            '\n * '.join(sorted(uncalculated_analysis))
          )
        )

      # This writes the per-target analysis to json for the cache. We should consider making a synthetic
      # target to hold an aggregated bag and speed up the noop case.
      analysis_product = {}
      for vt in invalidation_check.all_vts:
        target_analysis = {}
        if not vt.valid:
          for source in analyzable_sources_by_target[vt.target]:
            target_analysis[source] = calculated_analysis[source]
          target_analysis_bytes = json.dumps(target_analysis)
          with open(target_analysis_file(vt), 'wb') as f:
            f.write(target_analysis_bytes)

        with open(target_analysis_file(vt), 'rb') as f:
          analysis_bytes = f.read()
        target_analysis = json.loads(analysis_bytes)

        analysis_product.update(target_analysis)
      self.context.products.safe_create_data(self.analysis_product_name(), lambda: analysis_product)
