#!/usr/bin/env python3

import argparse
import subprocess
from pathlib import Path
from textwrap import dedent

from typing import List, Sequence, Set


def main() -> None:
  folders = create_parser().parse_args().folders
  for fp in get_files_with_import(folders):
    remove_builtins(file_path=fp)
    if safe_to_remove_future_from_build(file_path=fp):
      target_name = determine_pants_target_name(file_path=fp)
      update_build_dependencies(file_path=fp, pants_target_name=target_name)


def create_parser() -> argparse.ArgumentParser:
  parser = argparse.ArgumentParser(
    description='Remove `from builtins import x`, and possibly the BUILD entry for `future`.')
  parser.add_argument('folders', nargs='*')
  return parser


def get_files_with_import(folders: Sequence[str]) -> Set[Path]:
  return {
    fp
    for folder in folders
    for fp in Path(folder).rglob("*.py")
    if not fp.name.endswith("__init__.py")
    and "from builtins import" in fp.read_text()
  }


def determine_pants_target_name(file_path: Path) -> str:
  file_map = subprocess.run([
    './pants',
    'filemap',
    f'{file_path.parent}:'
  ], stdout=subprocess.PIPE, encoding="utf-8").stdout.strip().split('\n')
  target_entry = next((line for line in file_map if file_path.name in line), None)
  if target_entry is None:
    raise SystemExit(dedent(f"""\n
        ERROR: File '{file_path}' invalid. Not found anywhere in {file_path.parent}/BUILD."""))
  pants_target_path = target_entry.split(' ')[1]
  pants_target_name = pants_target_path.split(':')[1]
  return pants_target_name


def remove_builtins(*, file_path: Path) -> None:
  lines = file_path.read_text().splitlines()
  builtins_line_index = next(
    (i for i, line in enumerate(lines) if "from builtins" in line), None
  )
  if builtins_line_index:
    lines.pop(builtins_line_index)
    file_path.write_text("\n".join(lines) + "\n")


def safe_to_remove_future_from_build(*, file_path: Path) -> bool:
  lines = file_path.read_text().splitlines()
  return all(
    "from future.utils" not in line and
    "from future.moves" not in line
    for line in lines
  )


def _find_target_index_in_build(
  *, build_lines: List[str], pants_target_name: str, file_name: str
) -> int:
  index = next((i for i, line in enumerate(build_lines)
                if f"name = '{pants_target_name}'" in line
                or f"name='{pants_target_name}'" in line),
               None)
  if index is None:  # mono-target
    index = next((i for i, line in enumerate(build_lines) if file_name in line), None)
    if index is None:  # only one target block in file, and sources aren't specified
      index = next(i for i, line in enumerate(build_lines) if 'python_' in line and '(' in line)
  return index


def update_build_dependencies(*, file_path: Path, pants_target_name: str) -> None:
  build_file: Path = file_path.parent / "BUILD"
  lines = build_file.read_text().splitlines()
  target_index = _find_target_index_in_build(
    build_lines=lines, pants_target_name=pants_target_name, file_name=file_path.name
  )
  future_line_index = next(
    (i for i, line in enumerate(lines[target_index:]) if '3rdparty/python:future' in line), None
  )
  if future_line_index:
    lines.pop(future_line_index + target_index)
    build_file.write_text("\n".join(lines) + "\n")


if __name__ == '__main__':
  try:
    main()
  except KeyboardInterrupt:
    pass
