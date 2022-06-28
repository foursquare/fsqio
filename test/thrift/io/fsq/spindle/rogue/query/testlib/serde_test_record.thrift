// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

namespace java io.fsq.spindle.rogue.query.testlib.gen

include "io/fsq/spindle/rogue/testlib/types.thrift"

struct SerdeTestRecord {
  1:  optional types.ObjectId id (wire_name="_id")
  2:  optional string stringField
  3:  optional SerdeTestRecord subrecordField
  4:  optional map<string,i32> mapField
  5:  optional map<string,SerdeTestRecord> subrecordMapField
  6:  optional map<string,list<SerdeTestRecord>> subrecordListMapField
  7:  optional list<string> stringListField
  8:  optional list<SerdeTestRecord> subrecordListField
  9:  optional list<map<string,SerdeTestRecord>> subrecordMapListField
} (
  primary_key="id"
  mongo_identifier="rogue_mongo"
  mongo_collection="serde_test_records"
)
