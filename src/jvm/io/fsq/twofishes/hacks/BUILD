# Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

scala_library(
  name = 'hacks',
  sources = globs('*.scala'),
  dependencies = [
    '3rdparty:commons-logging',  # Runtime dep.
    '3rdparty:hadoop-common',
    '3rdparty:hbase',  # buildgen gets confused by the hacks
  ],
)
