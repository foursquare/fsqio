// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

namespace java io.fsq.spindle.benchmark.gen

include "io/fsq/spindle/runtime/structs/gen/structs.thrift"

typedef binary (enhanced_types="bson:ObjectId") ObjectId
typedef i64 (enhanced_types="bson:DateTime") DateTime

struct BenchmarkExample {
  1: optional ObjectId id
  2: optional i32 intField
  3: optional i64 longField
  4: optional string message
  5: optional structs.InnerStruct singleStruct
  6: optional list<structs.InnerStruct> structList
  7: optional list<i32> intList
  8: optional ObjectId id2
  9: optional DateTime dt
}