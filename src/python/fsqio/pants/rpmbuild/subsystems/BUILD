
python_library(
  sources = globs('*.py'),
  dependencies = [
    '3rdparty/python:pantsbuild.pants',
  ],
)

page(
  name='page',
  source='README.md',
  provides=[
    wiki_artifact(wiki=confluence,
      space='ENG',
      title='Pants Plugin: Remote Sources',
    ),
  ],
)
