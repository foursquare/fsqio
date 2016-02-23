# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'test',
  sources = globs('CompilerForNegativeTests.scala'),
  tags = [
    'opensource',
    'fscommon',
  ],
  dependencies = [
    '3rdparty:reflections',
    '3rdparty:specs2',
  ],
)

junit_tests(
  name = 'test_of_test',
  sources = globs('TestOfCompilerForNegativeTests.scala'),
  dependencies = [
    '3rdparty:junit',
    ':test',  # buildgen will remove this dep without this comment
  ],
)
