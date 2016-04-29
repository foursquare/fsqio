# Copyright 2016 Foursquare Labs Inc. All Rights Reserved.


def sjar(org, name, rev, url=None):
  scala_jar_version = '2.10'
  return jar(org = org, name = '%s_%s' % (name, scala_jar_version), rev = rev, url = url)


jar_library(
name = 'buildgen-emit-used-symbols',
 jars = [
   sjar(org = 'io.fsq', name = 'buildgen-emit-used-symbols', rev = '1.1.0'),
 ]
)


jar_library(
 name = 'buildgen-emit-exported-symbols',
 jars = [
   sjar(org = 'io.fsq', name = 'buildgen-emit-exported-symbols', rev = '1.1.0'),
 ]
)
