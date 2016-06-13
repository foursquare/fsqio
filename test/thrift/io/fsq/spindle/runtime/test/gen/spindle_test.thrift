// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

namespace java io.fsq.spindle.runtime.test.gen
include "io/fsq/spindle/runtime/structs/gen/structs.thrift"

typedef binary MyBinary
typedef binary (enhanced_types="bson:ObjectId") ObjectId


struct TestStructInnerStructNoString {
  1: optional bool aBool
  2: optional byte aByte
  3: optional i16 anI16
  4: optional i32 anI32
  5: optional i64 anI64
  6: optional double aDouble
  7: optional string aString
  8: optional binary aBinary
  9: optional structs.InnerStructNoString aStruct
  10: optional set<string> aSet
  11: optional list<i32> aList
  12: optional map<string, structs.InnerStructNoString> aMap
  13: optional MyBinary aMyBinary
  14: optional list<structs.InnerStructNoString> aStructList
} (
  preserve_unknown_fields="1"
)

struct TestStructNoUnknownFieldsTrackingInnerStructNoString {
  1: optional bool aBool
  2: optional byte aByte
  3: optional i16 anI16
  4: optional i32 anI32
  5: optional i64 anI64
  6: optional double aDouble
  7: optional string aString
  8: optional binary aBinary
  9: optional structs.InnerStructNoString aStruct
  10: optional set<string> aSet
  11: optional list<i32> aList
  12: optional map<string, structs.InnerStructNoString> aMap
  13: optional MyBinary aMyBinary
  14: optional list<structs.InnerStructNoString> aStructList
}

struct TestStructNoBoolRetiredFieldsNoUnknownFieldsTracking {
  2: optional byte aByte
  3: optional i16 anI16
  4: optional i32 anI32
  5: optional i64 anI64
  6: optional double aDouble
  7: optional string aString
  8: optional binary aBinary
  9: optional structs.InnerStruct aStruct
  10: optional set<string> aSet
  11: optional list<i32> aList
  12: optional map<string, structs.InnerStruct> aMap
  13: optional MyBinary aMyBinary
  14: optional list<structs.InnerStruct> aStructList
} (
  retired_ids="1"
  retired_wire_names="aBool"
)

struct TestStructInnerStructNoStringNoUnknownFieldsTracking {
  1: optional bool aBool
  2: optional byte aByte
  3: optional i16 anI16
  4: optional i32 anI32
  5: optional i64 anI64
  6: optional double aDouble
  7: optional string aString
  8: optional binary aBinary
  9: optional structs.InnerStructNoStringNoUnknownFieldsTracking aStruct
  10: optional set<string> aSet
  11: optional list<i32> aList
  12: optional map<string, structs.InnerStructNoStringNoUnknownFieldsTracking> aMap
  13: optional MyBinary aMyBinary
  14: optional list<structs.InnerStructNoStringNoUnknownFieldsTracking> aStructList
} (
  preserve_unknown_fields="1"
)

struct TestStructNoUnknownFieldsTrackingInnerStructNoStringNoUnknownFieldsTracking {
  1: optional bool aBool
  2: optional byte aByte
  3: optional i16 anI16
  4: optional i32 anI32
  5: optional i64 anI64
  6: optional double aDouble
  7: optional string aString
  8: optional binary aBinary
  9: optional structs.InnerStructNoStringNoUnknownFieldsTracking aStruct
  10: optional set<string> aSet
  11: optional list<i32> aList
  12: optional map<string, structs.InnerStructNoStringNoUnknownFieldsTracking> aMap
  13: optional MyBinary aMyBinary
  14: optional list<structs.InnerStructNoStringNoUnknownFieldsTracking> aStructList
}

struct TestStructInnerStructNoI32 {
  1: optional bool aBool
  2: optional byte aByte
  3: optional i16 anI16
  4: optional i32 anI32
  5: optional i64 anI64
  6: optional double aDouble
  7: optional string aString
  8: optional binary aBinary
  9: optional structs.InnerStructNoI32 aStruct
  10: optional set<string> aSet
  11: optional list<i32> aList
  12: optional map<string, structs.InnerStructNoI32> aMap
  13: optional MyBinary aMyBinary
  14: optional list<structs.InnerStructNoI32> aStructList
} (
  preserve_unknown_fields="1"
)

struct TestStructInnerStructNoI32RetiredFields {
  1: optional bool aBool
  2: optional byte aByte
  3: optional i16 anI16
  4: optional i32 anI32
  5: optional i64 anI64
  6: optional double aDouble
  7: optional string aString
  8: optional binary aBinary
  9: optional structs.InnerStructNoI32RetiredFields aStruct
  10: optional set<string> aSet
  11: optional list<i32> aList
  12: optional map<string, structs.InnerStructNoI32RetiredFields> aMap
  13: optional MyBinary aMyBinary
  14: optional list<structs.InnerStructNoI32RetiredFields> aStructList
} (
  preserve_unknown_fields="1"
)



struct TestStructNoUnknownFieldsTrackingExceptInnerStruct {
  1: optional bool aBool
  2: optional byte aByte
  3: optional i16 anI16
  4: optional i32 anI32
  5: optional i64 anI64
  6: optional double aDouble
  7: optional string aString
  8: optional binary aBinary
  9: optional structs.InnerStruct aStruct
  10: optional set<string> aSet
  11: optional list<i32> aList
  12: optional map<string, structs.InnerStruct> aMap
  13: optional MyBinary aMyBinary
  14: optional list<structs.InnerStruct> aStructList
}

struct TestStructInnerStructNoUnknownFieldsTracking {
  1: optional bool aBool
  2: optional byte aByte
  3: optional i16 anI16
  4: optional i32 anI32
  5: optional i64 anI64
  6: optional double aDouble
  7: optional string aString
  8: optional binary aBinary
  9: optional structs.InnerStructNoUnknownFieldsTracking aStruct
  10: optional set<string> aSet
  11: optional list<i32> aList
  12: optional map<string, structs.InnerStructNoUnknownFieldsTracking> aMap
  13: optional MyBinary aMyBinary
  14: optional list<structs.InnerStructNoUnknownFieldsTracking> aStructList
} (
  preserve_unknown_fields="1"
)

struct TestStructNoUnknownFieldsTracking {
  1: optional bool aBool
  2: optional byte aByte
  3: optional i16 anI16
  4: optional i32 anI32
  5: optional i64 anI64
  6: optional double aDouble
  7: optional string aString
  8: optional binary aBinary
  9: optional structs.InnerStructNoUnknownFieldsTracking aStruct
  10: optional set<string> aSet
  11: optional list<i32> aList
  12: optional map<string, structs.InnerStructNoUnknownFieldsTracking> aMap
  13: optional MyBinary aMyBinary
  14: optional list<structs.InnerStructNoUnknownFieldsTracking> aStructList
}


struct TestStructNoBoolNoUnknownFieldsTracking {
  2: optional byte aByte
  3: optional i16 anI16
  4: optional i32 anI32
  5: optional i64 anI64
  6: optional double aDouble
  7: optional string aString
  8: optional binary aBinary
  9: optional structs.InnerStruct aStruct
  10: optional set<string> aSet
  11: optional list<i32> aList
  12: optional map<string, structs.InnerStruct> aMap
  13: optional MyBinary aMyBinary
  14: optional list<structs.InnerStruct> aStructList
}

struct TestStructNoBoolRetiredFields {
  2: optional byte aByte
  3: optional i16 anI16
  4: optional i32 anI32
  5: optional i64 anI64
  6: optional double aDouble
  7: optional string aString
  8: optional binary aBinary
  9: optional structs.InnerStruct aStruct
  10: optional set<string> aSet
  11: optional list<i32> aList
  12: optional map<string, structs.InnerStruct> aMap
  13: optional MyBinary aMyBinary
  14: optional list<structs.InnerStruct> aStructList
} (
  preserve_unknown_fields="1"
  retired_ids="1"
  retired_wire_names="aBool"
)

struct TestStructMapStructKeysValues {
  1: optional map<structs.InnerStruct, structs.InnerStruct> mapStructs
}

struct TestStructIdentifierOverThousand {
  1001: optional bool aBool
}

exception TestFirstException {
  1: i32 value
}

exception TestSecondException {
  1: i32 value
}

exception TestThirdException {
  1: i32 value
}

service TestServices {
  i32 dummy1(1: i32 A) throws (1: TestFirstException ex1);
  i32 dummy2(1: i32 A) throws (1: TestFirstException ex1, 2: TestSecondException ex2);
  i32 dummy3(1: i32 A) throws (1: TestFirstException ex1, 2: TestSecondException ex2, 3: TestThirdException ex3);
}

service ChildTestServices extends TestServices {
  i32 dummy4(1: i32 A);
  oneway void dummyOneway(1: i32 A, 2: i32 B);
}
