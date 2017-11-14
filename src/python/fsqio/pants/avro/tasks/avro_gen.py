# coding=utf-8
# Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

from __future__ import (
  absolute_import,
  division,
  generators,
  nested_scopes,
  print_function,
  unicode_literals,
  with_statement,
)

import os

from pants.backend.jvm.targets.java_library import JavaLibrary
from pants.backend.jvm.tasks.nailgun_task import NailgunTask
from pants.base.exceptions import TaskError
from pants.java.jar.jar_dependency import JarDependency
from pants.option.custom_types import target_option
from pants.task.simple_codegen_task import SimpleCodegenTask

from fsqio.pants.avro.targets.java_avro_library import JavaAvroLibrary


_AVRO_REV = '1.8.2'


class AvroJavaGenTask(SimpleCodegenTask, NailgunTask):
  """Compile Avro schema, protocol, or IDL files to Java code."""

  @classmethod
  def register_options(cls, register):
    super(AvroJavaGenTask, cls).register_options(register)
    # pylint: disable=no-member
    cls.register_jvm_tool(register,
                          'avro-tools',
                          classpath=[JarDependency(org='org.apache.avro', name='avro-tools', rev=_AVRO_REV)],)
    register('--runtime-deps', advanced=True, type=list, member_type=target_option, fingerprint=True,
             default=['//:avro-{}'.format(_AVRO_REV)],
             help='A list of specs pointing to dependencies of Avro generated code.')

  def __init__(self, *args, **kwargs):
    super(AvroJavaGenTask, self).__init__(*args, **kwargs)

  def synthetic_target_type(self, target):
    return JavaLibrary

  def synthetic_target_extra_dependencies(self, target, target_workdir):
    return self.resolve_deps(self.get_options().runtime_deps)

  def is_gentarget(self, target):
    return isinstance(target, JavaAvroLibrary)

  def _avro(self, args):
    result = self.runjava(classpath=self.tool_classpath('avro-tools'),
                          main='org.apache.avro.tool.Main',
                          args=args)
    if result != 0:
      raise TaskError('avro-tools failed with exit status {}'.format(result))

  def _compile_schema(self, source, workdir):
    self._avro(['compile', 'schema', source, workdir])

  def _compile_protocol(self, source, workdir):
    self._avro(['compile', 'protocol', source, workdir])

  def _compile_idl(self, source, workdir):
    idl_filename = os.path.basename(source)
    idl_base_filename, _ = os.path.splitext(idl_filename)
    protocol_file = os.path.join(workdir, idl_base_filename + '.avpr')
    self._avro(['idl', source, protocol_file])
    self._compile_protocol(protocol_file, workdir)

  def execute_codegen(self, target, target_workdir):
    for source in target.sources_relative_to_buildroot():
      if source.endswith('.avsc'):
        self._compile_schema(source, target_workdir)
      elif source.endswith('.avpr'):
        self._compile_protocol(source, target_workdir)
      elif source.endswith('.avdl'):
        self._compile_idl(source, target_workdir)
      else:
        raise TaskError('Pants only understands Avro schema (.avsc), protocol (.avpr), and IDL (.avdl) files.')
