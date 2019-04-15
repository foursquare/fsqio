#!/usr/bin/env python3

import argparse
import subprocess
from functools import partial
from textwrap import dedent

from typing import List


def main() -> None:
  parser = create_parser()
  args = parser.parse_args()
  call_test = partial(call_pants_test, py2=args.two, debug=args.debug, include_integration=args.integration)
  if not args.no_clean:
    clean()
  if not args.file_names:
    path = determine_target_root(args.folder, args.contrib)
    call_test(f'{path}:')
    return
  for file_name in args.file_names:
    path = determine_pants_target_path(args, file_name)
    call_test(path)


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
  parser = argparse.ArgumentParser(description='Run unit tests in Python 3 mode.')
  parser.add_argument('folder', help='Target folder name, e.g. backend/jvm')
  parser.add_argument(
    'file_names',
    nargs='*',
    default=[],
    help='Specific .py file(s). Ignore this arg to run tests for whole folder.'
  )
  parser.add_argument('-2', '--two', action='store_true', help='Run in Python 2 mode.')
  parser.add_argument('-c', '--contrib', action='store_true', help='Operate on targets in contrib/.')
  parser.add_argument('-d', '--debug', action='store_true',
                      help='Turn off test timeout to avoid prematurely ending debugging session.')
  parser.add_argument('-i', '--integration', action='store_true', help='Include integration tests.')
  parser.add_argument('-n', '--no-clean', action='store_true',
                      help='Don\'t wipe the environment. This can be used to speed up process, so long as you want the '
                           'same interpreter as prior invocation.')
  return parser


TEST_BASE_ROOT = 'tests/python/pants_test'


def determine_pants_target_path(args, file_name: str) -> str:
  target_root = determine_target_root(args.folder, args.contrib)
  pants_target_name = determine_pants_target_name(target_root, file_name)
  return f'{target_root}:{pants_target_name}'


def determine_target_root(folder: str, is_contrib: bool) -> str:
  if is_contrib:
    target_folder_root = folder.split('/')[0]
    base_root = f'contrib/{target_folder_root}/{TEST_BASE_ROOT}/contrib'
  else:
    base_root = TEST_BASE_ROOT
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
# Call tests
# -------------------------------------------------

def clean() -> None:
  subprocess.run(['./pants', 'clean-all'])


def call_pants_test(pants_target_path: str, *,
                    py2: bool,
                    debug: bool,
                    include_integration: bool
                    ) -> None:
  debug_flag = '--no-test-pytest-timeouts'
  skip_integration_flag = "--tag=-integration"
  interpreter_version = 'CPython>=3.5' if not py2 else 'CPython<3'
  interpreter_flag = f'--python-setup-interpreter-constraints=["{interpreter_version}"]'
  command = ['./pants', interpreter_flag]
  if debug:
    command.append(debug_flag)
  if not include_integration:
    command.append(skip_integration_flag)
  command.extend(['test', pants_target_path])
  subprocess.run(command)


if __name__ == '__main__':
  try:
    main()
  except KeyboardInterrupt:
    pass
