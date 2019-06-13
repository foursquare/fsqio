#!/usr/bin/env python3

import argparse

from typing import List, Set, Sequence
from glob import glob


ENCODING_INDEX = 0
FUTURE_IMPORT_INDEX = 4


def main() -> None:
  folders = create_parser().parse_args().folders
  for fp in get_files(folders):
    with open(fp, "r") as f:
      lines = list(f.readlines())
    if is_py2_header(lines[:FUTURE_IMPORT_INDEX + 1]):
      rewrite(fp, lines)


def create_parser() -> argparse.ArgumentParser:
  parser = argparse.ArgumentParser(
    description='Use the new header without __future__ imports and # encoding.')
  parser.add_argument('folders', nargs='*')
  return parser


def get_files(folders: Sequence[str]) -> Set[str]:
  return {
    f
    for folder in folders
    for f in glob(f"{folder}/**/*.py", recursive=True)
    if not f.endswith("__init__.py")
  }


def is_py2_header(header: Sequence[str]) -> bool:
  return "# coding=utf-8" in header[ENCODING_INDEX] and "from __future__" in header[FUTURE_IMPORT_INDEX]


def rewrite(path: str, lines: List[str]) -> None:
  with open(path, "w") as f:
    f.writelines(
      lines[ENCODING_INDEX + 1:FUTURE_IMPORT_INDEX] + lines[FUTURE_IMPORT_INDEX + 2:]
    )


if __name__ == '__main__':
  try:
    main()
  except KeyboardInterrupt:
    pass
