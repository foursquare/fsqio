// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

namespace java io.fsq.rogue.spindle.test.gen

include "io/fsq/rogue/spindle/test/ids.thrift"
include "io/fsq/rogue/spindle/test/types.thrift"

typedef types.ThriftMetadata ThriftMetadata
typedef types.ObjectId ObjectId

struct ThriftLike {
  1: optional ThriftMetadata metadata
  2: optional ids.LikeId id (wire_name="_id")
  3: optional i64 userid (wire_name="userid")
  4: optional i64 checkin (wire_name="checkin")
  5: optional ObjectId tip (wire_name="tip")
} (
  primary_key="id"
  mongo_identifier="rogue_mongo"
  mongo_collection="likes"
)
