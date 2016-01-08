# Copyright 2016 Foursquare Labs, Inc. All Rights Reserved.

pom_target(
  name = 'exceptionator',
  prefixes = [
    'src/jvm/io/fsq/exceptionator',
  ],
  dependencies = [
    ':rogue',
    ':spindle',
    'src/jvm/io/fsq/exceptionator/actions',
    'src/jvm/io/fsq/exceptionator/actions/concrete',
    'src/jvm/io/fsq/exceptionator/filter',
    'src/jvm/io/fsq/exceptionator/filter/concrete',
    'src/jvm/io/fsq/exceptionator/loader/concrete',
    'src/jvm/io/fsq/exceptionator/loader/service',
    'src/jvm/io/fsq/exceptionator/model',
    'src/jvm/io/fsq/exceptionator/model/io',
    'src/jvm/io/fsq/exceptionator/service',
    'src/jvm/io/fsq/exceptionator/util',
  ],
  provides = artifact(
    org = 'com.foursquare.test',
    name = 'exceptionator',
    repo = internal_repo,
  ),
)

pom_target(
  name = 'fhttp',
  prefixes = [
    'src/jvm/io/fsq/fhttp',
  ],
  dependencies = [
    'src/jvm/io/fsq/fhttp',
  ],
  provides = artifact(
    org = 'com.foursquare.test',
    name = 'fhttp',
    repo = internal_repo,
  ),
)

pom_target(
  name = 'field',
  prefixes = [
    'src/jvm/io/fsq/field',
  ],
  dependencies = [
    'src/jvm/io/fsq/field',
  ],
  provides = artifact(
    org = 'com.foursquare.test',
    name = 'field',
    repo = internal_repo,
  ),
)

pom_target(
  name = 'macros',
  prefixes = [
    'src/jvm/io/fsq/macros',
  ],
  dependencies = [
    'src/jvm/io/fsq/macros',
  ],
  provides = artifact(
    org = 'com.foursquare.test',
    name = 'macros',
    repo = internal_repo,
  ),
)

pom_target(
  name = 'rogue',
  prefixes = [
    'src/jvm/io/fsq/rogue',
  ],
  dependencies = [
    ':field',
    ':spindle',
    'src/jvm/io/fsq/rogue',
    'src/jvm/io/fsq/rogue/index',
    'src/jvm/io/fsq/rogue/indexchecker',
    'src/jvm/io/fsq/rogue/lift',
    'src/jvm/io/fsq/rogue/spindle',
  ],
  provides = artifact(
    org = 'com.foursquare.test',
    name = 'rogue',
    repo = internal_repo,
  ),
)

pom_target(
  name = 'spindle',
  prefixes = [
    'src/jvm/io/fsq/spindle',
  ],
  dependencies = [
    ':field',
    'src/jvm/io/fsq/spindle/__shaded_for_spindle_bootstrap__/descriptors',
    'src/jvm/io/fsq/spindle/__shaded_for_spindle_bootstrap__/runtime',
    'src/jvm/io/fsq/spindle/codegen/binary',
    'src/jvm/io/fsq/spindle/codegen/parser',
    'src/jvm/io/fsq/spindle/codegen/runtime',
    'src/jvm/io/fsq/spindle/common/thrift/base',
    'src/jvm/io/fsq/spindle/common/thrift/bson',
    'src/jvm/io/fsq/spindle/common/thrift/json',
    'src/jvm/io/fsq/spindle/runtime',
  ],
  provides = artifact(
    org = 'com.foursquare.test',
    name = 'spindle',
    repo = internal_repo,
  ),
)

pom_target(
  name = 'twofishes',
  prefixes = [
    'src/jvm/io/fsq/twofishes',
  ],
  dependencies = [
    ':spindle',
    'src/jvm/io/fsq/twofishes/core',
    'src/jvm/io/fsq/twofishes/country',
    'src/jvm/io/fsq/twofishes/country/data',
    'src/jvm/io/fsq/twofishes/hacks',
    'src/jvm/io/fsq/twofishes/indexer/batch',
    'src/jvm/io/fsq/twofishes/indexer/data:langdetect',
    'src/jvm/io/fsq/twofishes/indexer/importers/geonames',
    'src/jvm/io/fsq/twofishes/indexer/mongo',
    'src/jvm/io/fsq/twofishes/indexer/output',
    'src/jvm/io/fsq/twofishes/indexer/scalding',
    'src/jvm/io/fsq/twofishes/indexer/scalding/output',
    'src/jvm/io/fsq/twofishes/indexer/util',
    'src/jvm/io/fsq/twofishes/replayer',
    'src/jvm/io/fsq/twofishes/server',
    'src/jvm/io/fsq/twofishes/server/resources/static',
    'src/jvm/io/fsq/twofishes/server/resources/warmup',
    'src/jvm/io/fsq/twofishes/util',
    'src/jvm/io/fsq/twofishes/util/data',
    'src/thrift/io/fsq/twofishes',
  ],
  provides = artifact(
    org = 'com.foursquare.test',
    name = 'twofishes',
    repo = internal_repo,
  ),
)
