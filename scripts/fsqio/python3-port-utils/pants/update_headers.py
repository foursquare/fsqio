#!/usr/bin/env python3

import argparse

from typing import List
from glob import glob


ENCODING_INDEX = 0
FUTURE_IMPORT_INDEX = 4


def main() -> None:
  folders = create_parser().parse_args().folders
  files = {
    f
    for folder in folders
    for f in glob(f"{folder}/**/*.py", recursive=True)
    if not f.endswith("__init__.py")
  }
  for file in files:
    with open(file, "r") as f:
      lines = list(f.readlines())
    if is_py2_header(lines[:FUTURE_IMPORT_INDEX + 1]):
      rewrite(file, lines)


def create_parser() -> argparse.ArgumentParser:
  parser = argparse.ArgumentParser(
    description='Use the new header without __future__ imports and # encoding.')
  parser.add_argument('folders', nargs='*')
  return parser


def is_py2_header(header: List[str]) -> bool:
  return "# coding=utf-8" in header[ENCODING_INDEX] and "from __future__" in header[FUTURE_IMPORT_INDEX]


def rewrite(path: str , lines: List[str]) -> None:
  with open(path, "w") as f:
    f.writelines(lines[ENCODING_INDEX + 1:FUTURE_IMPORT_INDEX] + lines[FUTURE_IMPORT_INDEX + 1:])


if __name__ == '__main__':
  try:
    main()
  except KeyboardInterrupt:
    pass
