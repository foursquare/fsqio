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

import os.path

from pants.fs.archive import TarArchiver
from pants.task.task import Task

from fsqio.pants.node.tasks.webpack import WebPack
from fsqio.pants.util.dirutil import safe_mkdir


class WebPackBundle(Task):
  @classmethod
  def prepare(cls, options, round_manager):
    super(WebPackBundle, cls).prepare(options, round_manager)
    round_manager.require_data(WebPack.Resources)

  @classmethod
  def product_types(cls):
    return ['webpack_bundle']

  def execute(self):
    targets = [t for t in self.context.targets() if isinstance(t, WebPack.Resources)]
    for target in targets:
      concrete_target = target.concrete_derived_from
      bundle_dir = os.path.join(self.get_options().pants_distdir, 'webpack-bundles', concrete_target.id)

      safe_mkdir(bundle_dir, clean=True)
      archiver = TarArchiver(mode='w:gz', extension='tgz')
      archiver.create(
        basedir=target.target_base,
        outdir=bundle_dir,
        name=concrete_target.id,
      )
