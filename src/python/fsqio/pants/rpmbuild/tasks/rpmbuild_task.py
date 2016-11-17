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

import os
import shutil
import subprocess
import tarfile
import uuid

from pants.base.build_environment import get_buildroot
from pants.base.exceptions import TaskError
from pants.base.generator import Generator
from pants.base.workunit import WorkUnit, WorkUnitLabel
from pants.task.task import Task
from pants.util.contextutil import temporary_dir
from pants.util.dirutil import safe_mkdir
from pkg_resources import resource_string

from fsqio.pants.rpmbuild.targets.rpm_spec import RpmSpecTarget


# Maps platform names to information about the specific platform.
# TODO(tdyas): Put this in pants.ini defaults.
PLATFORMS = {
  'centos6': {
    'base': 'centos:6.8',
  },
  'centos7': {
    'base': 'centos:7',
  },
}


class RpmbuildTask(Task):
  """Build a RpmSpecTarget into one or more RPMs using a consistent build environment
  in a Docker container.
  """
  @classmethod
  def register_options(cls, register):
    super(RpmbuildTask, cls).register_options(register)
    register(
      '--platform',
      default='centos7',
      help='Sets the platform to build RPMS for.',
    )
    register(
      '--docker',
      default='docker',
      help='Name of the docker command to invoke.'
    )
    register(
      '--keep-build-products',
      type=bool,
      advanced=True,
      help='Do not remove the build directory passed to Docker.'
    )

  def __init__(self, *args, **kwargs):
    super(RpmbuildTask, self).__init__(*args, **kwargs)

  @staticmethod
  def is_rpm_spec(target):
    return isinstance(target, RpmSpecTarget)

  @staticmethod
  def write_stream(r, w):
    size = 1024 * 1024  # 1 MB
    buf = r.read(size)
    while buf:
      w.write(buf)
      buf = r.read(size)

  def convert_build_req(self, raw_build_reqs):
    pkg_names = []
    for raw_build_req in raw_build_reqs.split(','):
      raw_build_req = raw_build_req.strip()
      pkg_name = raw_build_req.split(' ')[0]
      pkg_names.append(pkg_name)
    return pkg_names

  def extract_build_reqs(self, rpm_spec):
    build_reqs = []

    with open(rpm_spec, 'rb') as f:
      for line in f:
        line = line.strip().lower()
        if line.startswith('buildrequires'):
          raw_build_reqs = line.split(':', 1)[1].strip()
          build_reqs.extend(self.convert_build_req(raw_build_reqs))

    return build_reqs

  def docker_workunit(self, name, cmd):
    return self.context.new_workunit(
      name=name,
      labels=[WorkUnitLabel.RUN],
      log_config=WorkUnit.LogConfig(level=self.get_options().level, colors=self.get_options().colors),
      cmd=' '.join(cmd)
    )

  def build_rpm(self, platform, target, build_dir):
    # Copy the spec file to the build directory.
    rpm_spec_path = os.path.join(get_buildroot(), target.rpm_spec)
    shutil.copy(rpm_spec_path, build_dir)
    spec_basename = os.path.basename(target.rpm_spec)

    # Resolve the build requirements.
    build_reqs = self.extract_build_reqs(rpm_spec_path)

    # Copy local sources to the build directory.
    local_sources = []
    for source_rel_path in target.sources_relative_to_buildroot():
      shutil.copy(os.path.join(get_buildroot(), source_rel_path), build_dir)
      local_sources.append({
        'basename': os.path.basename(source_rel_path),
      })

    # Setup information on remote sources.
    remote_sources = [{'url': rs, 'basename': os.path.basename(rs)} for rs in target.remote_sources]

    # Write the entry point script.
    entrypoint_path = os.path.join(build_dir, 'build_rpm.sh')
    with open(entrypoint_path, 'wb') as f:
      f.write('#!/bin/sh\n')
      f.write('cd /home/rpmuser/rpmbuild/SPECS\n')
      f.write('rpmbuild -ba {}\n'.format(spec_basename))
    os.chmod(os.path.join(build_dir, 'build_rpm.sh'), 0555)

    # Write the Dockerfile for this build.
    generator = Generator(
      resource_string(__name__, 'dockerfile_template.mustache'),
      image=platform['base'],
      spec_basename=spec_basename,
      build_reqs={'reqs': ' '.join(build_reqs)} if build_reqs else None,
      local_sources=local_sources,
      remote_sources=remote_sources,
    )
    dockerfile_path = os.path.join(build_dir, 'Dockerfile')
    with open(dockerfile_path, 'wb') as f:
      f.write(generator.render())

    # Generate a UUID to identify the image.
    image_base_name = 'rpm-image-{}'.format(uuid.uuid4())
    image_name = '{}:latest'.format(image_base_name)
    container_name = None

    try:
      # Build the Docker image that will build the RPMS.
      build_image_cmd = [
        self.get_options().docker,
        'build',
        #'--no-cache',  # TODO(tdyas): Figure out if docker caching is trustworthy again.
        '-t',
        image_name,
        build_dir,
      ]
      with self.docker_workunit(name='build-image', cmd=build_image_cmd) as workunit:
        try:
          self.context.log.debug('Executing: {}'.format(' '.join(build_image_cmd)))
          subprocess.check_call(build_image_cmd, stdout=workunit.output('stdout'), stderr=workunit.output('stderr'))
        except subprocess.CalledProcessError as e:
          raise TaskError('Failed to build image: {0}'.format(e))

      # Run the image in a container to actually build the RPMs.
      container_name = 'rpm-builder-{}'.format(uuid.uuid4())
      run_container_cmd = [
        self.get_options().docker,
        'run',
        '--attach=stdout',
        '--attach=stderr',
        '--name={}'.format(container_name),
        image_name,
      ]
      with self.docker_workunit(name='run-container', cmd=run_container_cmd) as workunit:
        try:
          self.context.log.debug('Executing: {}'.format(' '.join(run_container_cmd)))
          subprocess.check_call(run_container_cmd,
                                stdout=workunit.output('stdout'),
                                stderr=workunit.output('stderr'))
        except subprocess.CalledProcessError as e:
          raise TaskError('Failed to run build container: {0}'.format(e))

      # Extract the built RPMs from the container.
      output_dir = os.path.join(get_buildroot(), 'dist', 'rpmbuild')
      safe_mkdir(output_dir)
      extract_rpms_cmd = [
        self.get_options().docker,
        'export',
        container_name,
      ]
      with self.docker_workunit(name='extract-rpms', cmd=extract_rpms_cmd) as workunit:
        proc = subprocess.Popen(extract_rpms_cmd, stdout=subprocess.PIPE, stderr=None)
        with tarfile.open(fileobj=proc.stdout, mode='r|*') as tar:
          for entry in tar:
            name = entry.name
            if (name.startswith('home/rpmuser/rpmbuild/RPMS/') or name.startswith('home/rpmuser/rpmbuild/SRPMS/')) and name.endswith('.rpm'):
              rel_rpm_path = name.lstrip('home/rpmuser/rpmbuild/')
              if rel_rpm_path:
                self.context.log.info('Extracting {}'.format(rel_rpm_path))
                fileobj = tar.extractfile(entry)
                safe_mkdir(os.path.join(output_dir, os.path.dirname(rel_rpm_path)))
                with open(os.path.join(output_dir, rel_rpm_path), 'wb') as f:
                  self.write_stream(fileobj, f)

        retcode = proc.wait()
        if retcode != 0:
          raise TaskError('Failed to extract RPMS')

    finally:
      # Remove the build container.
      if container_name:
        remove_container_cmd = [self.get_options().docker, 'rm', container_name]
        with self.docker_workunit(name='remove-build-container', cmd=remove_container_cmd) as workunit:
          subprocess.call(remove_container_cmd, stdout=workunit.output('stdout'), stderr=workunit.output('stderr'))

      # Remove the build image.
      remove_image_cmd = [self.get_options().docker, 'rmi', image_name]
      with self.docker_workunit(name='remove-build-image', cmd=remove_image_cmd) as workunit:
        subprocess.call(remove_image_cmd, stdout=workunit.output('stdout'), stderr=workunit.output('stderr'))

  def execute(self):
    platform_key = self.get_options().platform
    try:
      platform = PLATFORMS[platform_key]
    except KeyError:
      raise TaskError('Unknown platform {}'.format(platform_key))

    for target in self.context.targets(self.is_rpm_spec):
      with temporary_dir(cleanup=not self.get_options().keep_build_products) as build_dir:
        self.context.log.debug('Build directory: {}'.format(build_dir))
        self.build_rpm(platform, target, build_dir)
