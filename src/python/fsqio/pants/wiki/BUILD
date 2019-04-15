
python_library(
  sources = globs('*.py'),
  dependencies = [
    '3rdparty/python:pantsbuild.pants',
    'src/python/fsqio/pants/wiki/subsystems',
    'src/python/fsqio/pants/wiki/tasks',
  ],
)


page(
  name='page',
  source='README.md',
  links=[
    'src/python/fsqio/pants/buildgen/core',
    'src/python/fsqio/pants/buildgen:page',
  ],
  provides=[
    wiki_artifact(wiki=confluence,
      space='ENG',
      title='Pants Confluence Plugin',
    ),
  ],
)
