#!/usr/bin/env python3

import argparse
import itertools
import subprocess
import sys
import re
from glob import glob
from textwrap import dedent

from typing import List, NamedTuple


def main() -> None:
  parser = create_parser()
  args = parser.parse_args()
  # preview changes needed for file
  if not args.file_names:
    target_root = determine_target_root(args.folder, args.contrib, args.test)
    check_what_needs_changes(target_root, args.root_only)
    return
  # futurize files
  for file_name in args.file_names:
    paths = determine_paths(args, file_name)
    if args.preview:
      preview_changes(paths.file_path)
      continue
    futurize_diff = call_futurize(paths.file_path)
    if not futurize_made_changes(futurize_diff):
      continue
    if new_imports_added(futurize_diff):
      update_build_dependencies(paths.target_root, paths.pants_target_name, file_name)
      call_pants_fmt(paths.pants_target_path)
    prompt_review_of_diffs(futurize_diff)
    if not args.no_tests and file_changed(paths.file_path):
      call_pants_test(paths.pants_test_path)


# --------------------------------------------------
# Command line utils
# -------------------------------------------------

def get_stdout(command: List[str]) -> str:
  return subprocess.run(
    command, 
    stdout=subprocess.PIPE, 
    encoding='utf-8') \
    .stdout.strip()


def get_stderr(command: List[str]) -> str:
  return subprocess.run(
    command, 
    stderr=subprocess.PIPE, 
    encoding='utf-8') \
    .stderr.strip()


# --------------------------------------------------
# Setup
# -------------------------------------------------

def create_parser() -> argparse.ArgumentParser:
  parser = argparse.ArgumentParser(description='Run futurize script over targets.')
  parser.add_argument('folder', help='Target folder name, e.g. backend/jvm')
  parser.add_argument(
    'file_names',
    nargs='*',
    default=[],
    help='Specific .py file(s). Ignore this arg to see changes necessary in folder.'
  )
  parser.add_argument('-t', '--test', action='store_true', help='Operate on test targets.')
  parser.add_argument('-p', '--preview', action='store_true', help='Do not write changes.')
  parser.add_argument('-n', '--no-tests', action='store_true', help='Skip unit tests.')
  parser.add_argument('-r', '--root-only', action='store_true', help='Do not recursively search subfolders.')
  parser.add_argument('-c', '--contrib', action='store_true', help='Operate on targets in contrib/.')
  return parser


class Paths(NamedTuple):
  target_root: str
  file_path: str
  pants_target_name: str
  pants_target_path: str
  pants_test_path: str


SRC_BASE_ROOT = 'src/python/pants'
TEST_BASE_ROOT = 'tests/python/pants_test'


def determine_paths(args, file_name: str) -> Paths:
  target_root = determine_target_root(args.folder, args.contrib, args.test)
  test_root = determine_target_root(args.folder, args.contrib, is_test=True)
  pants_target_name = determine_pants_target_name(target_root, file_name)
  file_path = f'{target_root}/{file_name}'
  pants_target_path = f'{target_root}:{pants_target_name}'
  pants_test_path = f'{test_root}:{pants_target_name}'
  return Paths(
      target_root=target_root,
      file_path=file_path,
      pants_target_name=pants_target_name,
      pants_target_path=pants_target_path,
      pants_test_path=pants_test_path
    )


def determine_target_root(folder: str, is_contrib: bool, is_test: bool) -> str:
  if is_contrib:
    target_folder_root = folder.split('/')[0]
    base_root = (f'contrib/{target_folder_root}/{TEST_BASE_ROOT}/contrib'
                if is_test
                else f'contrib/{target_folder_root}/{SRC_BASE_ROOT}/contrib')
  else:
    base_root = TEST_BASE_ROOT if is_test else SRC_BASE_ROOT
  return f'{base_root}/{folder}' if folder else base_root


def determine_pants_target_name(target_root: str, file_name: str) -> str:
  file_map = get_stdout([
    './pants',
    'filemap',
    f'{target_root}:'
    ]).split('\n')
  target_entry = next((line for line in file_map if file_name in line), None)
  if target_entry is None:
    raise SystemExit(dedent(f"""\n
        ERROR: File name '{file_name}' invalid. Not found anywhere in {target_root}/BUILD."""))
  pants_target_path = target_entry.split(' ')[1]
  pants_target_name = pants_target_path.split(':')[1]
  return pants_target_name


# --------------------------------------------------
# Futurize script
# -------------------------------------------------

FUTURIZE_BIN = 'build-support/pants_dev_deps.venv/bin/futurize'


def check_what_needs_changes(folder_root: str, root_only: bool) -> None:
  file_paths = (glob(f'{folder_root}/*.py', recursive=False) 
                if root_only
                else glob(f'{folder_root}/**/*.py', recursive=True))
  futurize_output = get_stderr([
    FUTURIZE_BIN,
    '--stage2',
    '--no-diffs'
    ] + file_paths) \
    .split('\n')

  errors_dropped = itertools.takewhile(
      lambda line: not re.match('RefactoringTool:.*error:', line),
      futurize_output)
  ignore_unnecessary_lines = itertools.dropwhile(
      lambda line: 'RefactoringTool: Files that need to be modified:' not in line,
      errors_dropped)

  remove_refactoring_text = [line.replace('RefactoringTool: ', '') for line in ignore_unnecessary_lines]
  no_header = list(remove_refactoring_text)[1:]
  if not no_header:
    print('Folder is already Python 3 compatible 🐍 🎉')
    return
  split_by_warning: List[List[str]] = [list(group) for k, group 
                                        in itertools.groupby(no_header, 
                                                              lambda line: 'Warnings/messages while refactoring:' in line) 
                                        if not k]
  if len(split_by_warning) == 2:  # warnings
    print('Warnings while refactoring:\n' + '\n'.join(split_by_warning[1]) + '\n\n', 
      file=sys.stderr)
  dropped_warnings = split_by_warning[0]

  def drop_prefix(line: str) -> str:
    return (line.split(f'{TEST_BASE_ROOT}/')[1]
    if TEST_BASE_ROOT in line
    else line.split(f'{SRC_BASE_ROOT}/')[1])
  remove_path_prefix = [drop_prefix(line) for line in dropped_warnings]

  if 'contrib' in folder_root:
    remove_path_prefix = [line.split('contrib/')[1] for line in remove_path_prefix]
  formatted_for_cli = ([f"{line.split('/')[-1]}" for line in remove_path_prefix]
                      if root_only
                      else [f"{'/'.join(line.split('/')[:-1])} {line.split('/')[-1]}" for line in remove_path_prefix])
  delimiter = '\n' if not root_only else ' '
  print(delimiter.join(sorted(formatted_for_cli)))


def preview_changes(file_path: str) -> None:
  subprocess.run([
    FUTURIZE_BIN,
    '--stage2',
    file_path
  ])


def call_futurize(file_path: str) -> str:
  return get_stdout([
    FUTURIZE_BIN,
    '--stage2',
    '--write',
    '--nobackup',
    file_path
  ])


# --------------------------------------------------
# Check for changes
# -------------------------------------------------

def file_changed(file_path: str) -> bool:
  git_changes = get_stdout(['git', 'ls-files', '-m'])
  return file_path in git_changes


def futurize_made_changes(futurize_output: str) -> bool:
  return bool(futurize_output)


def new_imports_added(futurize_output: str) -> bool:
  return 'import' in futurize_output


# --------------------------------------------------
# Update BUILD
# -------------------------------------------------

def _find_target_index_in_build(build_lines: List[str], pants_target_name: str, file_name: str) -> int:
  index = next((i for i, line in enumerate(build_lines) 
              if f"name = '{pants_target_name}'" in line 
              or f"name='{pants_target_name}'" in line), 
              None)
  if index is None:  # mono-target
    index = next((i for i, line in enumerate(build_lines) if file_name in line), None)
    if index is None:  # only one target block in file, and sources aren't specified
      index = next(i for i, line in enumerate(build_lines) if 'python_' in line and '(' in line)
  return index


def _future_dependency_already_added(lines: List[str], starting_index: int) -> bool:
  for line in lines[starting_index:]:
    if '3rdparty/python:future' in line:
      return True
    if ')\n' in line:  # done with dependencies section
      return False


def update_build_dependencies(folder_root: str, pants_target_name: str, file_name: str) -> None:
  build_file = f'{folder_root}/BUILD'
  with open(build_file, 'r') as f:
    lines = list(f.readlines())
  target_index = _find_target_index_in_build(lines, pants_target_name, file_name)  
  if _future_dependency_already_added(lines, target_index):
    return
  for i, line in enumerate(lines[target_index:]):
    if 'dependencies = [' in line or 'dependencies=[' in line:
      lines.insert(target_index + i + 1, "    '3rdparty/python:future',\n")
      break
    if ')\n' in line:  # dependencies section doesn't exist for target
      lines.insert(target_index + i, '  dependencies = [\n')
      lines.insert(target_index + i + 1, "    '3rdparty/python:future',\n")
      lines.insert(target_index + i + 2, '  ],\n')
      break
  with open(build_file, 'w') as f:
    f.writelines(lines)


# --------------------------------------------------
# Pants goals
# -------------------------------------------------

def call_pants_fmt(pants_target_path: str) -> None:
  subprocess.run([
    './pants',
    'fmt',
    pants_target_path
  ])


def call_pants_test(pants_test_target_path: str) -> None:
  subprocess.run([
    './pants',
    'test',
    pants_test_target_path
  ])


# --------------------------------------------------
# Prompt review of diffs
# -------------------------------------------------

def prompt_review_of_diffs(futurize_output: str) -> None:
  input(dedent(f"""\
  ----------------------------------------------------------------------
  
  Review the file for changes and make modifications if necessary. 
  
  ----------------------------------------------------------------------
  
  {futurize_output}
  
  
  ----------------------------------------------------------------------
  
  Input the enter key when ready to move on."""))


if __name__ == '__main__':
  try:
    main()
  except KeyboardInterrupt:
    pass
