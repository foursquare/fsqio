// Copyright 2022 Foursquare Labs Inc. All Rights Reserved.

namespace java io.fsq.spindle.rogue.testlib.gen

include "io/fsq/spindle/rogue/testlib/types.thrift"

typedef types.ObjectId ObjectId

struct InnerShardKeyStruct {
   1: optional ObjectId id (wire_name="_id")
   2: optional string someString (wire_name="s")
}

struct TestShardKeyStruct {
   1: optional ObjectId id (wire_name="_id")
   2: optional double someDouble (wire_name="d")
   3:  optional InnerShardKeyStruct innerStruct (wire_name="i")
} (primary_key="id"
   shard_key="innerStruct.id:hashed"
)
