
python_library(
  name = 'spindle',
  sources = globs('*.py'),
  dependencies = [
    '3rdparty/python:pantsbuild.pants',
    'src/python/fsqio/pants/buildgen/core/subsystems',
    'src/python/fsqio/pants/buildgen/jvm/scala',
  ],
  tags = [
    'opensource',
    'buildgen',
    'dependencies_must_have:opensource',
    'dependencies_must_have:buildgen',
  ],
  provides=setup_py(
    name='fsqio.pants.buildgen.spindle',
    version='1.3.1',
    description='Pants buildgen for Python projects.',
    url='https://github.com/foursquare/fsqio',
    license='Apache License, Version 2.0',
    zip_safe=True,
    namespace_packages=['fsqio'],
    classifiers=[
      'Intended Audience :: Developers',
      'License :: OSI Approved :: Apache Software License',
      'Operating System :: MacOS :: MacOS X',
      'Operating System :: POSIX :: Linux',
      'Programming Language :: Python',
      'Topic :: Software Development :: Build Tools',
      'Topic :: Software Development :: Testing',
    ],
  ),
)
