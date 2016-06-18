// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

namespace java io.fsq.common.thrift.descriptors.types.gen

include "io/fsq/common/thrift/descriptors/headers/gen/headers.thrift"

enum SimpleBaseType {
  BOOL = 0,
  BYTE = 1,
  I16 = 2,
  I32 = 3,
  I64 = 4,
  DOUBLE = 5,
  STRING = 6,
  BINARY = 7
}

struct BaseType {
  1: required SimpleBaseType simpleBaseType,
  99: optional list<headers.Annotation> annotations = []
}

// Thrift struct definitions cannot be recursive, so container types cannot contain
// their element types directly. Instead they indirect via a type id that can be
// dereferenced from a TypeRegistry.
// Note that type ids are opaque, not durable, and can change from parse to parse.

struct ListType {
  1: required string elementTypeId
}

struct SetType {
  1: required string elementTypeId
}

struct MapType {
  1: required string keyTypeId,
  2: required string valueTypeId
}

// Exactly one of these must be present. In languages with support for 'union' (currently
// Java and Ruby) this will be enforced automatically. In other languages this is just a
// regular struct, and we enforce the union constraint in code.
// TODO: Add Python support for 'union' in the Thrift compiler.
union SimpleContainerType {
  1: optional ListType listType,
  2: optional SetType setType,
  3: optional MapType mapType
}

struct ContainerType {
  1: required SimpleContainerType simpleContainerType,
  99: optional list<headers.Annotation> annotations = []
}

// A reference to a type by its name or typedef'd alias.
struct Typeref {
  1: required string typeAlias
}

// Exactly one of these must be present. In languages with support for 'union' (currently
// Java and Ruby) this will be enforced automatically. In other languages this is just a
// regular struct, and we enforce the union constraint in code.
// TODO: Add Python support for 'union' in the Thrift compiler.
union SimpleType {
  1: optional BaseType baseType,
  2: optional ContainerType containerType,
  3: optional Typeref typeref
}

struct Type {
  1: required string id,
  2: required SimpleType simpleType
}

struct Typedef {
  1: required string typeId,
  2: required string typeAlias,
  99: optional list<headers.Annotation> annotations = []
} (generate_proxy="true")

// A registry of all the types referenced in a thrift program.
//
// Note that identical types are not unique: E.g., two different mentions of list<string>
// will get two different type ids. This is necessary, since types can be annotated.
struct TypeRegistry {
  // A map from id to type. Used to resolve type ids, e.g., in container types.
  // Note that type ids are opaque, not durable, and can change from parse to parse.
  1: map<string, Type> idToType,

  // A map from alias to type id. Aliases are created using typedef.
  2: map<string, string> aliasToTypeId
}
