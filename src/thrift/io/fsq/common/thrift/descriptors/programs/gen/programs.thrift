// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

namespace java io.fsq.common.thrift.descriptors.programs.gen

include "io/fsq/common/thrift/descriptors/constants/gen/constants.thrift"
include "io/fsq/common/thrift/descriptors/enums/gen/enums.thrift"
include "io/fsq/common/thrift/descriptors/headers/gen/headers.thrift"
include "io/fsq/common/thrift/descriptors/services/gen/services.thrift"
include "io/fsq/common/thrift/descriptors/structs/gen/structs.thrift"
include "io/fsq/common/thrift/descriptors/types/gen/types.thrift"

// In the Thrift parsing code the collection of all elements in a .thrift file
// is referred to as a 'program'.
struct Program {
  1: optional list<headers.Namespace> namespaces = [],
  2: optional list<headers.Include> includes = [],
  3: optional list<constants.Const> constants = [],
  4: optional list<enums.Enum> enums = [],
  5: optional list<types.Typedef> typedefs = [],
  6: optional list<structs.Struct> structs = [],
  7: optional list<structs.Union> unions = [],
  8: optional list<structs.Exception> exceptions = [],
  9: optional list<services.Service> services = [],

  // A registry of all types in the program. Used for resolving type references.
  98: required types.TypeRegistry typeRegistry
} (generate_proxy="true")
