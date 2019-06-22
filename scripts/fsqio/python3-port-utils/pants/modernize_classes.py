#!/usr/bin/env python3

import argparse
import re
from pathlib import Path

from typing import Sequence, Set

SUPER_REGEX = r"super\([a-zA-Z]+, [a-z]+\)"
OBJECT_REGEX = r"class (?P<className>[a-zA-Z]*)\(object\):"


def main() -> None:
  folders = create_parser().parse_args().folders
  for fp in get_relevant_files(folders):
    simplify(file_path=fp, regex=SUPER_REGEX, replacement="super()")
    simplify(file_path=fp, regex=OBJECT_REGEX, replacement=r"class \g<className>:")


def create_parser() -> argparse.ArgumentParser:
  parser = argparse.ArgumentParser(
    description='Remove `from builtins import x`, and possibly the BUILD entry for `future`.')
  parser.add_argument('folders', nargs='*')
  return parser


def get_relevant_files(folders: Sequence[str]) -> Set[Path]:
  return {
    fp
    for folder in folders
    for fp in Path(folder).rglob("*.py")
    if any(
      re.search(SUPER_REGEX, line) or re.search(OBJECT_REGEX, line)
      for line in fp.read_text().splitlines()
    )
  }


def simplify(*, file_path: Path, regex: str, replacement: str) -> None:
  lines = file_path.read_text().splitlines()
  indexes = [i for i, line in enumerate(lines) if re.search(regex, line)]
  for index in indexes:
    new_line = re.sub(regex, replacement, lines[index])
    lines[index] = new_line
  file_path.write_text("\n".join(lines) + "\n")


if __name__ == '__main__':
  try:
    main()
  except KeyboardInterrupt:
    pass
