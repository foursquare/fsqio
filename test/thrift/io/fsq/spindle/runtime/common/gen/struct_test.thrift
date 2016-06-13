// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

namespace java io.fsq.spindle.runtime.common.gen

include "io/fsq/spindle/runtime/structs/gen/structs.thrift"

typedef binary MyBinary
typedef binary (enhanced_types="bson:ObjectId") ObjectId

// A struct with a field of each type.

struct TestStruct {
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
} (
  preserve_unknown_fields="1"
)


// Identical structs, with one field missing. Useful for testing forwards wire compatibility, that is that you can
// read a serialized struct into an out-of-date version that's missing fields, and they are skipped correctly.

struct TestStructNoBool {
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
)



struct TestStructNoByte {
  1: optional bool aBool
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
)

struct TestStructNoI16 {
  1: optional bool aBool
  2: optional byte aByte
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
)

struct TestStructNoI32 {
  1: optional bool aBool
  2: optional byte aByte
  3: optional i16 anI16
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
)

struct TestStructNoI64 {
  1: optional bool aBool
  2: optional byte aByte
  3: optional i16 anI16
  4: optional i32 anI32
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
)

struct TestStructNoDouble {
  1: optional bool aBool
  2: optional byte aByte
  3: optional i16 anI16
  4: optional i32 anI32
  5: optional i64 anI64
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
)

struct TestStructNoString {
  1: optional bool aBool
  2: optional byte aByte
  3: optional i16 anI16
  4: optional i32 anI32
  5: optional i64 anI64
  6: optional double aDouble
  8: optional binary aBinary
  9: optional structs.InnerStruct aStruct
  10: optional set<string> aSet
  11: optional list<i32> aList
  12: optional map<string, structs.InnerStruct> aMap
  13: optional MyBinary aMyBinary
  14: optional list<structs.InnerStruct> aStructList
} (
  preserve_unknown_fields="1"
)

struct TestStructNoBinary {
  1: optional bool aBool
  2: optional byte aByte
  3: optional i16 anI16
  4: optional i32 anI32
  5: optional i64 anI64
  6: optional double aDouble
  7: optional string aString
  9: optional structs.InnerStruct aStruct
  10: optional set<string> aSet
  11: optional list<i32> aList
  12: optional map<string, structs.InnerStruct> aMap
  13: optional MyBinary aMyBinary
  14: optional list<structs.InnerStruct> aStructList
} (
  preserve_unknown_fields="1"
)

struct TestStructNoStruct {
  1: optional bool aBool
  2: optional byte aByte
  3: optional i16 anI16
  4: optional i32 anI32
  5: optional i64 anI64
  6: optional double aDouble
  7: optional string aString
  8: optional binary aBinary
  10: optional set<string> aSet
  11: optional list<i32> aList
  12: optional map<string, structs.InnerStruct> aMap
  13: optional MyBinary aMyBinary
  14: optional list<structs.InnerStruct> aStructList
} (
  preserve_unknown_fields="1"
)

struct TestStructNoStructList {
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
} (
  preserve_unknown_fields="1"
)

struct TestStructNoSet {
  1: optional bool aBool
  2: optional byte aByte
  3: optional i16 anI16
  4: optional i32 anI32
  5: optional i64 anI64
  6: optional double aDouble
  7: optional string aString
  8: optional binary aBinary
  9: optional structs.InnerStruct aStruct
  11: optional list<i32> aList
  12: optional map<string, structs.InnerStruct> aMap
  13: optional MyBinary aMyBinary
  14: optional list<structs.InnerStruct> aStructList
} (
  preserve_unknown_fields="1"
)

struct TestStructNoMap {
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
  13: optional MyBinary aMyBinary
  14: optional list<structs.InnerStruct> aStructList
} (
  preserve_unknown_fields="1"
)

struct TestStructNoMyBinary {
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
  14: optional list<structs.InnerStruct> aStructList
} (
  preserve_unknown_fields="1"
)

struct TestStructOidList {
  1: optional bool aBool
  2: optional list<ObjectId> anObjectIdList
  3: optional i32 anI32
}

struct TestStructNoList {
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
  12: optional map<string, structs.InnerStruct> aMap
  13: optional MyBinary aMyBinary
  14: optional list<structs.InnerStruct> aStructList
} (
  preserve_unknown_fields="1"
)


struct TestStructCollections {
  1: optional list<bool> listBool
  2: optional list<byte> listByte
  3: optional list<i16> listI16
  4: optional list<i32> listI32
  5: optional list<i64> listI64
  6: optional list<double> listDouble
  7: optional list<string> listString
  8: optional list<binary> listBinary
  9: optional list<structs.InnerStruct> listStruct
  11: optional set<bool> setBool
  12: optional set<byte> setByte
  13: optional set<i16> setI16
  14: optional set<i32> setI32
  15: optional set<i64> setI64
  16: optional set<double> setDouble
  17: optional set<string> setString
  18: optional set<binary> setBinary
  19: optional set<structs.InnerStruct> setStruct
  21: optional map<string, bool> mapBool
  22: optional map<string, byte> mapByte
  23: optional map<string, i16> mapI16
  24: optional map<string, i32> mapI32
  25: optional map<string, i64> mapI64
  26: optional map<string, double> mapDouble
  27: optional map<string, string> mapString
  28: optional map<string, binary> mapBinary
  29: optional map<string, structs.InnerStruct> mapStruct
} (
  preserve_unknown_fields="1"
)

struct TestStructNestedCollections {
  1: optional list<list<bool>> listBool
  2: optional list<list<byte>> listByte
  3: optional list<list<i16>> listI16
  4: optional list<list<i32>> listI32
  5: optional list<list<i64>> listI64
  6: optional list<list<double>> listDouble
  7: optional list<list<string>> listString
  8: optional list<list<binary>> listBinary
  9: optional list<list<structs.InnerStruct>> listStruct
  11: optional set<set<bool>> setBool
  12: optional set<set<byte>> setByte
  13: optional set<set<i16>> setI16
  14: optional set<set<i32>> setI32
  15: optional set<set<i64>> setI64
  16: optional set<set<double>> setDouble
  17: optional set<set<string>> setString
  18: optional set<set<binary>> setBinary
  19: optional set<set<structs.InnerStruct>> setStruct
  21: optional map<string, map<string, bool>> mapBool
  22: optional map<string, map<string, byte>> mapByte
  23: optional map<string, map<string, i16>> mapI16
  24: optional map<string, map<string, i32>> mapI32
  25: optional map<string, map<string, i64>> mapI64
  26: optional map<string, map<string, double>> mapDouble
  27: optional map<string, map<string, string>> mapString
  28: optional map<string, map<string, binary>> mapBinary
  29: optional map<string, map<string, structs.InnerStruct>> mapStruct
} (
  preserve_unknown_fields="1"
)
