# NOTE(mateo):
# This target exists in order to trigger a unit test upon changes. The env.sh contract was broken by a recent refactor
# and the hope is to build up some protections.

# TODO: Any BUILD files outside of a source root should migrate to build-support/ (may require namespacing for Fsq.io).
# TODO: Convert this to a 'files' target when moving to Pants 1.4.0.
resources(
  sources = [
    'env.sh'
  ],
  scope='runtime',
)

page(
  name='page',
  source='README.md',
  provides=[
    wiki_artifact(wiki=confluence,
      space='ENG',
      title='Foursquare.web: Upkeep Cache Management',
    ),
  ],
)
