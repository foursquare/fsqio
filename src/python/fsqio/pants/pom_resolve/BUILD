# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

python_library(
  sources = globs('*.py'),
  dependencies = [
    '3rdparty/python:pantsbuild.pants',
    '3rdparty/python:requests',
    '3rdparty/python:requests-futures',
    '3rdparty/python:twitter.common.collections',
  ],
  provides=setup_py(
    name='fsqio.pants.pom_resolve',
    version='1.3.0',
    description='Pom-resolve is an alternative jar resolution framework for Pants build system.',
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
  ),
)
