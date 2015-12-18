// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

namespace java io.fsq.rogue.spindle.test.gen

include "io/fsq/rogue/spindle/test/ids.thrift"
include "io/fsq/rogue/spindle/test/types.thrift"

typedef types.ThriftMetadata ThriftMetadata
typedef types.ObjectId ObjectId

struct ThriftOneCommentBson {
  1: optional string timestamp (wire_name="timestamp")
  2: optional i64 userid (wire_name="userid")
  3: optional string comment (wire_name="comment")
}

struct ThriftComment {
  1: optional ThriftMetadata metadata
  2: optional ids.CommentId id (wire_name="_id")
  3: optional list<ThriftOneCommentBson> comments (wire_name="comments")
} (
  primary_key="id"
  index="id:asc"
  mongo_identifier="rogue_mongo"
  mongo_collection="comments"
)
