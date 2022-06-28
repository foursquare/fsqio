// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

namespace java io.fsq.spindle.rogue.testlib.gen

struct TestStruct {
  1: optional i32 id (wire_name="_id")
  2: optional string info
} (
  primary_key="id"
  mongo_collection="test_structs"
  mongo_identifier="rogue_mongo"
)
