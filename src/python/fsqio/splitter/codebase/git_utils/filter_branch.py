# coding=utf-8
# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function

import logging
import shlex
import subprocess


GIT_FILTER_BRANCH = """filter-branch -f"""
RECOGNIZED_FILTERS = ('commit', 'tree', 'msg', 'env', 'parent', 'index', 'tag-name')
logger = logging.getLogger(__name__)


class UnsupportedFilterBranchCommand(ValueError):
  """Raise on unsupported filter-branch filters or args."""


def git_filter_branch(filter_type, shell_command, commit_range=None):
  """Run a git filter-branch command.

  The filter type must be one of the recognized filters, all of which are invoked similarly to
     git filter-branch --tree-filter <command> commit_range

  :param string shell_command: Shell command that will be run over each commit.
  :param string filter_type: Type of filter, i.e. 'env' or 'index'.
  :param string commit_range: Commits to operate upon. Works for tags, branches and git set_notation (e.g.
    'HEAD~10..HEAD' or 'master' or '-- --all'). Default is transforming the entire history of the checked out branch!
  """
  # The functions in this file will transform the git history of any branch or commit_range it is pointed at.
  # It is purposefully scrubbed afterwards so be pretty careful if/when using.

  # # Make sure that we are operating over a valid commit range.
  sanity_check_cmd = 'git log -1 {}'.format(commit_range)
  proc = subprocess.Popen(sanity_check_cmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
  _, err = proc.communicate()
  if err:
    raise Exception("Commit range {} not found!\n\tRan:  {}\n\tError: {}\n".format(commit_range, sanity_check_cmd, err))

  if filter_type not in RECOGNIZED_FILTERS:
    raise UnsupportedFilterBranchCommand(
      'Filter is unknown, must be one of: {}\nWas: {}'.format(RECOGNIZED_FILTERS, filter_type)
    )
  filter_command = """git {} --{}-filter \'{}\'""".format(GIT_FILTER_BRANCH, filter_type, shell_command)
  run_filter_branch(shell_command=filter_command, commit_range=commit_range)


def run_filter_branch(shell_command, commit_range=None):
  """Run a git filter-branch command.

  :param string shell_command: Command that will be run over each commit.
  """

  git_command = "{} {}".format(shell_command, commit_range)
  print("Running: {}".format(git_command))

  proc = subprocess.Popen(shlex.split(git_command), stdin=subprocess.PIPE, stderr=subprocess.PIPE)
  proc.wait()
  # This catches errors in the shell_command passed to filter-branch.
  if proc.returncode != 0:
    raise Exception("\nSomething went wrong.\n\nRan:\n {}\n\n"
                    "Error:\n {}\n \n ".format(git_command, ' '.join(proc.stderr.readlines())))
