
python_library(
  name = 'core',
  sources = globs('*.py'),
  dependencies = [
    '3rdparty/python:ansicolors',
    '3rdparty/python:pantsbuild.pants',
    'src/python/fsqio/pants/buildgen/core/subsystems',
  ],
  provides=setup_py(
    name='fsqio.pants.buildgen.core',
    version='1.3.0',
    description='Core libraries supporting Pants buildgen.',
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
    ]
  )
)