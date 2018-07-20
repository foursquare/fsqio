# coding=utf-8
# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function, unicode_literals

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


# NOTE(mateo):
# safe_deep_mkdir and makedirs_with_mode are not proven safe for Pants plugins and are POC from an Fsqio standpoint.
def safe_deep_mkdir(directory, clean=False, mode=0o777):
  """Ensure a directory is present recursively, with permission bits set.

  If it's not there, create it.  If it is, no-op. If clean is True, ensure the dir is empty."""
  if clean:
    safe_rmtree(directory)
  try:
    makedirs_with_mode(directory, mode)
  except OSError as e:
    if e.errno != errno.EEXIST:
      raise


def makedirs_with_mode(name, mode=0o777):
  """makedirs_with_mode(path [, mode=0777])

  Super-mkdir; create a leaf directory and all intermediate ones.
  Works like mkdir, except that any intermediate path segment (not
  just the rightmost) will be created if it does not exist.  This is
  recursive. Also explicitly chmod's the created dirs since os.mkdirs does not guarantee that
  permissions are correctly set on all systems.

  """
  head, tail = os.path.split(name)
  if not tail:
    head, tail = os.path.split(head)
  if head and tail and not os.path.exists(head):
    try:
      makedirs_with_mode(head, mode)
    except OSError as e:
      # be happy if someone already created the path
      if e.errno != errno.EEXIST:
        raise
    if tail == os.curdir:           # xxx/newdir/. exists if xxx/newdir exists
      return
  os.mkdir(name, mode)
  os.chmod(name, mode)
