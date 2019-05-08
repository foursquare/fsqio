# coding=utf-8
# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function

import json
import logging
import os
import subprocess

from argparse import ArgumentParser

from fsqio.splitter.codebase.git_utils.filter_branch import git_filter_branch


logger = logging.getLogger(__name__)


def change_file_locations(commit_range=None, file_moves_map=None):
  """Rewrite the git history to have a file's location changed for every commit".

  The general idea is that you overwrite files that have hard-to-extract internal coupling with edited versions.
  :param dict file_moves_map: A dictionary of string:string mapping file paths from {source: dest}.
  """
  command = []
  for move in file_moves_map:
    # The existance check is shell because it is run filter branch and presumably the file doesn't exist ~always.
    command.extend(["""
    if [ -f {file_to_move} ];
      then mkdir -p `dirname {new_location}` && mv -f {file_to_move} {new_location};
    fi;""".format(file_to_move=move, new_location=file_moves_map[move])])
  if file_moves_map and command:
    logger.info("Splitter: Moving file locations")
    git_filter_branch(filter_type='tree', shell_command=' '.join(command), commit_range=commit_range)
  else:
    logger.info("No file moves requested.")


def anonymize_contributors(
  commit_range=None,
  default_author_name=None,
  default_email=None,
  contributor_file=None,
  contributor_ref=None,
  mailmap_file=None,
  mailmap_ref=None,
):
  """Anonymize the commit name and email credits not listed in the contributors file.

  The contributor file should have a list of emails, one per-line.

  The opt-in and mailmap locations are checked for every commit. You can set the '--backport-*' options to True if you
  want that check to always use HEAD, otherwise it will check those files as they were when each commit landed.
  """
  command = """
  OPTED_IN=$(git show {contributor_ref}:{contributor_file})
  export MAILMAP="mailmap.blob={mailmap_ref}:{mailmap_file}"
  """.format(
    contributor_ref=contributor_ref,
    contributor_file=contributor_file,
    mailmap_ref=mailmap_ref,
    mailmap_file=mailmap_file,
  )
  # Leaving the commented out debugging because this was a pain to get right and you never know.
  command += """
  orig=$GIT_AUTHOR_EMAIL
  mapped=$(git -c $MAILMAP check-mailmap "<$GIT_AUTHOR_EMAIL>")
  if [[ ! $OPTED_IN =~ $mapped ]]; then
    GIT_AUTHOR_NAME="{default_author_name}";
    GIT_AUTHOR_EMAIL="{default_email}";
    GIT_COMMITTER_NAME="{default_author_name}";
    GIT_COMMITTER_EMAIL="{default_email}";
  else
    GIT_AUTHOR_EMAIL=$mapped;
  fi
  # echo -e Email: $orig set as: $GIT_AUTHOR_EMAIL. $orig was mapped as: $mapped\n
  """.format(default_author_name=default_author_name, default_email=default_email)
  logger.info("Redacting unknown contributors.")
  git_filter_branch(filter_type='tree', shell_command=command, commit_range=commit_range)


def remove_patterns_from_commit_message(commit_range=None, match_patterns=None, line_patterns=None):
  """Delete any line of a commit message that contains a match with any pattern in "patterns".

  :param list[string] match_patterns: regex that can be understood by sed, removes only matches themselves.
  :param list[string] line_patterns: regex that can be understood by sed, removes any line containing a match.
  """
  command = ''
  if line_patterns:
    command += "sed -n \"/{}/!p\"".format(line_patterns[0])
    for pattern in line_patterns[1:]:
      # Don't try and redact newlines.
      if pattern:
        command += """ | sed -n \"/{}/!p\"""".format(pattern)

  if match_patterns:
    if command:
      command += " | "
    command += """sed "s/{}//g\"""".format(match_patterns[0])
    for pattern in match_patterns[1:]:
      # Don't try and redact newlines.
      if pattern:
        command += """ |  sed "s/{}//g\"""".format(pattern)
  if command:
    command += """ | cat -s"""
    logger.info("Redacting commit messages.")
    git_filter_branch(filter_type='msg', shell_command=command, commit_range=commit_range)
  else:
    logger.info("No redaction patterns found!")


def permanently_transform_opensource_repo():
  """
  Filter and permanently edit the history of a git branch. This will catastrophically rewrite your git history,
  you should not run this function.

  This runs every transformation needed by Foursquare's opensource repo.
  :param string commit_range: Commit range ( e.g. 'master', 'release-1.4.0', 'HEAD~10..HEAD')
  """
  head = subprocess.check_output('git log -1 --pretty=%H', shell=True).strip()
  parser = ArgumentParser('Rewrite git history with filter-branch to replace files and redact commit messages.')
  parser.add_argument(
    '--commit-range',
    default='_sapling_split_opensource_',
    help="Git range to process. Accepts rev-parse ranges (e.g. 'master', 'my_branch', 'HEAD~10..HEAD', etc)."
         "Default matches the branch created by sapling_util."
  )
  # Default author details for any commits with an author that is not included in the contributor file.
  parser.add_argument(
    '--default-author-name',
    default="Opensource",
    help="Commits by authors not mentioned in the contributors list will rewritten to credit this name."
  )
  parser.add_argument(
    '--default-email',
    default="opensource@foursquare.com",
    help="Commits by authors not mentioned in the contributors list will be recredited to this email."
  )
  # Default refs speficy the revision to source the file from, even when operating over older commits.
  #
  # This is mostly intended to support one of the following methods:
  #   * "HEAD"
  #       - (aka as it existed at the time of each commit)
  #   * None
  #       - Passing None falls back to the default, reading the file from HEAD of the working (NOT target) branch.
  parser.add_argument(
    '--contributor-file',
    help="File path to contributor list, a text file with one email list per-line."
         "Commits authored by anyone not in this file will be credited to the default author."
  )
  parser.add_argument(
    '--contributor-ref',
    default=head,
    help="Use the contributor file from this ref. Pass 'HEAD' to read the file as it existed when the commit landed."
         "NOTE: Anything other than HEAD allows updating the contibutor file to overwrite existing history.",
  )
  parser.add_argument(
    '--mailmap-file',
    default='.mailmap',
    help="Mailmap file path."
  )
  parser.add_argument(
    '--mailmap-ref',
    default=head,
    help="Use the mailmap file from this ref.  Pass 'HEAD' to read the file as it existed when the commit landed."
         "NOTE: Anything other than HEAD allows updating the mailmap to overwrite existing history.",
  )
  parser.add_argument(
    '--line-redactions-file',
    default=None,
    help="Commit message redaction. File path of text file, with a single sed regex on each line"
         "Removes the entire line from any commit message matching these patterns.",
  )
  parser.add_argument(
    '--match-redactions-file',
    default=None,
    help="Commit message redaction. File path of text file, with a single sed regex on each line"
         "Removes only the matches themselves from commit messages.",
  )
  parser.add_argument(
    '--file-moves-json-file',
    help="JSON file with every key/value being a mapping of file moves from { source: dest}."
         "When rewriting commits, the SRC file, if present at the time of the commit, overwrites the DEST at that sha.",
  )

  def get_lines_from_text_file(file_path):
    if file_path is None:
      yield
    elif not os.path.isfile(file_path):
      raise ValueError("No file found: {}".format(file_path))
    else:
      with open(file_path, 'rb') as file_path:
        for line in file_path.read().split('\n'):
          yield line

  args = parser.parse_args()
  git_commit_range = args.commit_range

  anonymize_contributors(
    commit_range=git_commit_range,
    default_author_name=args.default_author_name,
    default_email=args.default_email,
    contributor_file=args.contributor_file,
    contributor_ref=args.contributor_ref,
    mailmap_file=args.mailmap_file,
    mailmap_ref=args.mailmap_ref,
  )

  line_redactions_file = args.line_redactions_file
  match_redactions_file = args.match_redactions_file
  line_patterns = list(get_lines_from_text_file(line_redactions_file)) if line_redactions_file else None
  match_patterns = list(get_lines_from_text_file(match_redactions_file)) if match_redactions_file else None

  remove_patterns_from_commit_message(
    commit_range=git_commit_range, match_patterns=match_patterns, line_patterns=line_patterns
  )

  moves_json = args.file_moves_json_file
  if moves_json is not None:
    if not os.path.isfile(moves_json):
      raise ValueError("No file found: {}".format(moves_json))
    with open(moves_json, 'rb') as moves:
      file_moves_dict = json.load(moves)
      change_file_locations(commit_range=git_commit_range, file_moves_map=file_moves_dict)

if __name__ == '__main__':
  permanently_transform_opensource_repo()
