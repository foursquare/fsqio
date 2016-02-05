# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'geonames',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:akka',
    '3rdparty:casbah',
    '3rdparty:commons-net',
    '3rdparty:country-revgeo',
    '3rdparty:gt-shapefile',
    '3rdparty:icu4j',
    '3rdparty:json4s',
    '3rdparty:jts',
    '3rdparty:language-detect',
    '3rdparty:mongodb',
    '3rdparty:ostrich',
    '3rdparty:rockymadden',
    '3rdparty:s2',
    '3rdparty:salat',
    '3rdparty:scopt',
    '3rdparty:slf4s',
    'src/jvm/io/fsq/common/scala',
    'src/jvm/io/fsq/twofishes/core',
    'src/jvm/io/fsq/twofishes/country',
    'src/jvm/io/fsq/twofishes/indexer/data:langdetect',
    'src/jvm/io/fsq/twofishes/indexer/mongo',
    'src/jvm/io/fsq/twofishes/indexer/output',
    'src/jvm/io/fsq/twofishes/indexer/util',
    'src/jvm/io/fsq/twofishes/util',
    'src/thrift/io/fsq/twofishes',
  ],
)

jvm_binary(
  name = 'geonames-parser',
  main = 'io.fsq.twofishes.indexer.importers.geonames.GeonamesParser',
  dependencies = [
    '3rdparty:logback',  # Runtime dep.
    ':geonames',
  ],
)
