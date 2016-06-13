// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

namespace java io.fsq.spindle.runtime.structs.gen

typedef binary MyBinary
typedef binary (enhanced_types="bson:ObjectId") ObjectId


struct StructWithNoFields {
} (
  preserve_unknown_fields="1"
)

struct InnerStruct {
  1: optional string aString
  2: optional i32 anInt
} (
  preserve_unknown_fields="1"
)

struct InnerStructNoUnknownFieldsTracking {
  1: optional string aString
  2: optional i32 anInt
}

struct InnerStructNoString {
  2: optional i32 anInt
} (
  preserve_unknown_fields="1"
)

struct InnerStructNoStringNoUnknownFieldsTracking {
  2: optional i32 anInt
}

struct InnerStructNoI32 {
  1: optional string aString
} (
  preserve_unknown_fields="1"
)

struct InnerStructNoI32RetiredFields {
  1: optional string aString
} (
  preserve_unknown_fields="1"
  retired_ids="2"
  retired_wire_names="anInt"
)
