// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

namespace java io.fsq.common.thrift.descriptors.services.gen

include "io/fsq/common/thrift/descriptors/headers/gen/headers.thrift"
include "io/fsq/common/thrift/descriptors/structs/gen/structs.thrift"

struct Function {
  1: required string name,
  2: optional string returnTypeId,  // Unspecified means void.
  3: optional bool oneWay = 0,  // Thrift doesn't allow 'false'/'true' when specifying the default.
  4: required list<structs.Field> argz,
  5: required list<structs.Field> throwz,
  99: optional list<headers.Annotation> annotations = []
} (generate_proxy="true")

struct Service {
  1: required string name,
  2: optional string extendz,
  3: required list<Function> functions,
  99: optional list<headers.Annotation> annotations = []
} (generate_proxy="true")

