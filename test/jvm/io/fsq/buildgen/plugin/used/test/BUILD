# Copyright 2019 Foursquare Labs Inc. All Rights Reserved.

junit_tests(
  name = 'test',
  sources = globs('*.scala'),
  tags = [],
  dependencies = [
    '3rdparty:jackson-scala',
    '3rdparty:junit',
    '3rdparty:reflections',
    '3rdparty:scala-reflect',
    '3rdparty:scalac',
    ':test-files',
    'src/jvm/io/fsq/buildgen/plugin/used:emit-used-symbols',
    'src/jvm/io/fsq/common/scala',
  ],
)

files(
  name = 'test-files',
  sources = [
    'SampleFileForPluginTests.scala',
  ],
  scope="runtime",
)
