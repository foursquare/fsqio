#!/usr/bin/env python3

import argparse
import shutil
import subprocess
import re
from textwrap import dedent

from typing import List, NamedTuple


def main() -> None:
  check_ripgrep_installed()
  parser = create_parser()
  args = parser.parse_args()
  if not args.file_names:
    target_root = determine_target_root(args.folder, args.test, args.contrib)
    check_what_needs_changes(target_root, args.root_only)
    return
  for file_name in args.file_names:
    path = determine_path(args, file_name)
    old_usages = check_usages(path)
    prompt_review_of_file(file_name)
    for usage in old_usages:
      review(path, usage)


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


def check_ripgrep_installed() -> None:
  if not shutil.which('rg'):
    raise SystemExit('Ripgrep must be installed. See https://github.com/BurntSushi/ripgrep#installation.')


# --------------------------------------------------
# Setup
# -------------------------------------------------

def create_parser() -> argparse.ArgumentParser:
  parser = argparse.ArgumentParser(description='Modernize sets to use set literals and set comprehensions.')
  parser.add_argument('folder', help='Target folder name, e.g. backend/jvm')
  parser.add_argument(
    'file_names',
    nargs='*',
    default=[],
    help='Specific .py file(s). Ignore this arg to see changes necessary in folder.'
  )
  parser.add_argument('-t', '--test', action='store_true', help='Operate on test targets.')
  parser.add_argument('-r', '--root-only', action='store_true', help='Do not recursively search subfolders.')
  parser.add_argument('-c', '--contrib', action='store_true', help='Operate on targets in contrib/.')
  return parser


SRC_BASE_ROOT = 'src/python/pants'
TEST_BASE_ROOT = 'tests/python/pants_test'


def determine_path(args, file_name: str) -> str:
  target_root = determine_target_root(args.folder, args.test, args.contrib)
  return f'{target_root}/{file_name}'


def determine_target_root(folder: str, is_test: bool, is_contrib: bool) -> str:
  if is_contrib:
    target_folder_root = folder.split('/')[0]
    base_root = (f'contrib/{target_folder_root}/{TEST_BASE_ROOT}/contrib'
                 if is_test
                 else f'contrib/{target_folder_root}/{SRC_BASE_ROOT}/contrib')
  else:
    base_root = TEST_BASE_ROOT if is_test else SRC_BASE_ROOT
  return f'{base_root}/{folder}' if folder else base_root


# --------------------------------------------------
# Grep
# -------------------------------------------------

REGEX = r'set\(\[|set\(\(|set\(.*for'


def check_what_needs_changes(folder_root: str, root_only: bool) -> None:
  target = f"{folder_root}/*.py" if root_only else f"{folder_root}/**/*.py"
  grep_output = get_stdout(['rg', '-l', REGEX, '-g', target]).split('\n')
  remove_unnecessary = [p for p in grep_output if p]
  if not remove_unnecessary:
    print('No verbose set constructor usages 🐍 🎉')
    return
  pretty_printed = format_for_cli(remove_unnecessary, root_only)
  print(pretty_printed)


def format_for_cli(file_paths: List[str], root_only: bool) -> str:
  def drop_prefix(line: str) -> str:
    return (line.split(f'{TEST_BASE_ROOT}/')[1]
            if TEST_BASE_ROOT in line
            else line.split(f'{SRC_BASE_ROOT}/')[1])
  remove_path_prefix = [drop_prefix(line) for line in file_paths]

  if 'contrib' in file_paths[0]:
    remove_path_prefix = [line.split('contrib/')[1] for line in remove_path_prefix]
  formatted_for_cli = ([f"{line.split('/')[-1]}" for line in remove_path_prefix]
                       if root_only
                       else [f"{'/'.join(line.split('/')[:-1])} {line.split('/')[-1]}" for line in remove_path_prefix])
  delimiter = '\n' if not root_only else ' '
  return delimiter.join(sorted(formatted_for_cli))



# --------------------------------------------------
# Review usage
# -------------------------------------------------

class Usage(NamedTuple):
  line: str
  line_no: int


def check_usages(file_path: str) -> List[Usage]:
  rg_search = get_stdout(['rg', REGEX, file_path]).split('\n')
  stripped_rg_search = [line.strip() for line in rg_search]
  with open(file_path, 'r') as f:
    lines = f.readlines()
    stripped_lines = [line.strip() for line in lines]
  return [Usage(line, stripped_lines.index(line)) for line in stripped_rg_search]


def prompt_review_of_file(file_path: str) -> None:
  print(dedent(f"""\
  ----------------------------------------------------------------------
  
  Beginning review of {file_path}. 
  
  For every usage, input `y` to accept the script's fix or `n` to reject it and move on.
  
  ----------------------------------------------------------------------
  
  """))


def review(file_path: str, usage: Usage) -> None:
  new_line = generate_fix(usage.line)
  print(f'Original ({usage.line_no + 1}): {usage.line}')
  print(f'Proposed fix: {new_line}')
  answer = input()
  if answer == 'y':
    rewrite(file_path, new_line, usage.line_no)
  print('\n')


def _generate_frozenset_fix(line: str) -> str:
  list_regex = r'\(\[(?P<literal>.*)\]\)'
  tuple_regex = r'\(\((?P<literal>.*)\)\)'
  list_fix = re.sub(list_regex, '({\g<literal>})', line)
  tuple_fix = re.sub(tuple_regex, '({\g<literal>})', list_fix)
  return tuple_fix


def _generate_set_fix(line: str) -> str:
  list_regex = r'set\(\[(?P<literal>.*)\]\)'
  tuple_regex = r'set\(\((?P<literal>.*)\)\)'
  list_fix = re.sub(list_regex, '{\g<literal>}', line)
  tuple_fix = re.sub(tuple_regex, '{\g<literal>}', list_fix)
  return tuple_fix

def _generate_comprehension_fix(line: str) -> str:
  regex = r'set\((?P<comp>.*)\)'
  return re.sub(regex, '{\g<comp>}', line)

def generate_fix(line: str) -> str:
  if 'frozenset' in line:
    return _generate_frozenset_fix(line)
  elif 'for' in line and 'in' in line:
    return _generate_comprehension_fix(line)
  return _generate_set_fix(line)


def rewrite(file_path: str, new_line: str, line_no: int) -> None:
  with open(file_path, 'r') as f:
    lines = list(f.readlines())
  white_space = re.match(r' *', lines[line_no]).group(0)
  lines[line_no] = f'{white_space}{new_line}\n'
  with open(file_path, 'w') as f:
    f.writelines(lines)


if __name__ == '__main__':
  try:
    main()
  except KeyboardInterrupt:
    pass
