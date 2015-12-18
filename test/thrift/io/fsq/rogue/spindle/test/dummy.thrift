// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

namespace java io.fsq.rogue.spindle.test.gen

// Empty mongo model used for QueryExecutorTest
struct ThriftDummy {
  1: optional i32 id (wire_name="_id")  // Spindle bails if there's no primary_key.
} (
  primary_key="id"
  mongo_identifier="rogue_mongo"
  mongo_collection="dummy"
)
