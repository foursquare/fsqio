# coding=utf-8
# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

from __future__ import (
  absolute_import,
  division,
  generators,
  nested_scopes,
  print_function,
  unicode_literals,
  with_statement,
)

import errno
import os
import shutil


def safe_rmtree(directory):
  """Delete a directory if it's present. If it's not present, no-op."""
  shutil.rmtree(directory, ignore_errors=True)


def safe_mkdir(directory, clean=False):
  """Ensure a directory is present.

  If it's not there, create it.  If it is, no-op. If clean is True, ensure the dir is empty."""
  if clean:
    safe_rmtree(directory)
  try:
    os.makedirs(directory)
  except OSError as e:
    if e.errno != errno.EEXIST:
      raise

