# coding=utf-8
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function, unicode_literals

import os.path
from zipfile import ZIP_DEFLATED

from pants.fs.archive import ZipArchiver
from pants.task.task import Task
from pants.util.dirutil import safe_mkdir

from fsqio.pants.node.targets.webpack_module import NpmResource
from fsqio.pants.node.tasks.webpack import WebPack


class WebPackBundle(Task):
  @classmethod
  def prepare(cls, options, round_manager):
    super(WebPackBundle, cls).prepare(options, round_manager)
    round_manager.require_data(WebPack.Resources)

  @classmethod
  def product_types(cls):
    return ['webpack_bundle']

  @property
  def cache_target_dirs(self):
    return False

  def execute(self):

    targets = [t for t in self.context.targets() if isinstance(t, WebPack.Resources)]
    for target in targets:
      concrete_target = target.concrete_derived_from
      if not isinstance(concrete_target, NpmResource):
        bundle_dir = os.path.join(self.get_options().pants_distdir, 'webpack-bundles', concrete_target.id)

        safe_mkdir(bundle_dir, clean=True)
        archiver = ZipArchiver(ZIP_DEFLATED, extension='zip')
        archiver.create(
          basedir=target.target_base,
          outdir=bundle_dir,
          name=concrete_target.id,
        )
