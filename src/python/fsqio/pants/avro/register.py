# coding=utf-8
# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

from __future__ import (
  absolute_import,
  division,
  generators,
  nested_scopes,
  print_function,
  unicode_literals,
  with_statement,
)

from pants.build_graph.build_file_aliases import BuildFileAliases
from pants.goal.task_registrar import TaskRegistrar as task

from fsqio.pants.avro.targets.java_avro_library import JavaAvroLibrary
from fsqio.pants.avro.tasks.avro_gen import AvroJavaGenTask


def build_file_aliases():
  return BuildFileAliases(
    targets={
      JavaAvroLibrary.alias(): JavaAvroLibrary,
    }
  )


def register_goals():
  task(name='avro-java', action=AvroJavaGenTask).install('gen')
