// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

namespace java io.fsq.common.thrift.descriptors.structs.gen

include "io/fsq/common/thrift/descriptors/headers/gen/headers.thrift"

/* Structs, unions and exceptions. */

enum Requiredness {
  REQUIRED = 0,
  OPTIONAL = 1
} (generate_proxy="true")

struct Field {
  1: required i16 identifier,
  2: required string name,
  3: required string typeId,
  4: optional Requiredness requiredness,
  5: optional string defaultValue,
  99: optional list<headers.Annotation> annotations = []
} (generate_proxy="true")

struct Struct {
  1: required string name,
  2: required list<Field> fields,
  99: optional list<headers.Annotation> annotations = []
} (generate_proxy="true")

struct Union {
  1: required string name,
  2: required list<Field> fields,
  99: optional list<headers.Annotation> annotations = []
} (generate_proxy="true")

struct Exception {
  1: required string name,
  2: required list<Field> fields,
  99: optional list<headers.Annotation> annotations = []
} (generate_proxy="true")
