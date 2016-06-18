// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

namespace java io.fsq.common.thrift.descriptors.headers.gen

struct Include {
  1: required string path
}

struct Namespace {
  1: required string language,
  2: required string name
}

struct Annotation {
  1: required string key,
  2: required string value
}
