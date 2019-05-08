# coding=utf-8
# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

from __future__ import absolute_import, division, print_function

from textwrap import dedent

from argparse import ArgumentParser


def process_paths_file(source_file=None):
  """This returns a tuple of (files, directories) from the given source_file.

  This is intended to separate files from directories, and only works with the invariant that items in the
  file end with a '/' if they are directories.
  """
  with open(source_file, 'rb') as f:
    files = set()
    directories = set()
    lines = f.read().splitlines()
    for line in lines:
      line.strip()
      if line.startswith('#'):
        pass
      # If adding a prefix(as opposed to a file) to the file list, it is required to end with a slash.
      elif line.endswith('/'):
        directories.add(line)
      else:
        # Check for blank lines.
        if line:
          files.add(line)
    return files, directories


def create_sapling_text(split_name, source_file):
  # TODO(mateo): Allow multiple splits in a single sapling run.
  # We original made this DSL for sapling config because we didn't want to
  # have a python distribution coupled to a git hook. We don't use the git hook
  # anymore and maybe we should allow upstream sapling config.
  files, directories = process_paths_file(source_file)
  all_oss_prefixes = sorted(files | directories)
  sapling_text = dedent(
    """
    {split_name} = {{
      'name': '{split_name}',
      'paths': [""".format(split_name=split_name)
  )
  for prefix in all_oss_prefixes:
    sapling_text += """\n    '{}',""".format(prefix)
  sapling_text += dedent("""
        ],
      }}
      splits = [
        {split_name},
      ]
  """.format(split_name=split_name))
  return sapling_text


def write_sapling_file(source_file=None, target_file=None):
  """Codegenerate a Sapling config file.

  Sapling config files are valid Python and read by a python interpreter. Since invoking Python is not
  ideal for all environments, this allows defining these paths in a text file.
  Our use case was having git hooks and the sapling build to share a common source of truth.

  Example paths file:

    $ cat fsqio/opensource_files.txt
    # This is the list of files that are split out to opensource.
    # If you are adding a whole directory, it MUST end with a '/'!

    3rdparty/BUILD.opensource
    3rdparty/python/BUILD.opensource
    BUILD.opensource
    build-support/fsqio/
    fsqio/deployed_files/
    pants
    pants.ini
    scripts/fsqio/
    src/docs/fsqio/
    src/jvm/io/fsq/
    src/python/fsqio/
    src/resources/io/fsq/
    src/thrift/io/fsq/
    test/python/fsqio_test/
    test/jvm/fsqio.tests.policy
    test/jvm/io/fsq/
    test/thrift/io/fsq/
    upkeep

  As the note says - directories must be declared by ending them with a '/'.
  """
  parser = ArgumentParser('Write a sapling config file')
  parser.add_argument(
    '-i',
    '--paths-file',
    required=True,
    help="Path containing paths to split.\nNew-line delimited text file, directories required to end with a '/'.",
  )
  parser.add_argument(
    '-o',
    '--output-file',
    default='.saplings',
    help='Output path for the generated sapling config.',
  )
  parser.add_argument(
    '-s',
    '--split-name',
    default='opensource',
    help='Name for the split (will affect the name of the git branch created by Sapling).',
  )
  args = parser.parse_args()

  source_file = args.paths_file
  sapling_config = args.output_file
  split_name = args.split_name
  with open(sapling_config, 'wb') as target_file:
    target_file.write(create_sapling_text(split_name, source_file))


if __name__ == '__main__':
  write_sapling_file()
