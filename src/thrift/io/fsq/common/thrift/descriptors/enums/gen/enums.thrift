// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

namespace java io.fsq.common.thrift.descriptors.enums.gen

include "io/fsq/common/thrift/descriptors/headers/gen/headers.thrift"

struct EnumElement {
  1: required string name,
  2: required i32 value,
  99: optional list<headers.Annotation> annotations = []
} (generate_proxy="true")

struct Enum {
  1: required string name,
  2: required list<EnumElement> elements,
  99: optional list<headers.Annotation> annotations = []
} (generate_proxy="true")
