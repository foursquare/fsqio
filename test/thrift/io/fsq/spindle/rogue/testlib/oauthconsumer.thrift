// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

namespace java io.fsq.spindle.rogue.testlib.gen

include "io/fsq/spindle/rogue/testlib/ids.thrift"
include "io/fsq/spindle/rogue/testlib/types.thrift"

typedef types.ThriftMetadata ThriftMetadata
typedef types.ObjectId ObjectId

enum ThriftConsumerPrivilege {
  awardBadges = 0 (string_value="Award badges")
}

struct ThriftOAuthConsumer {
  1: optional ThriftMetadata metadata
  2: optional ids.OAuthConsumerId id (wire_name="_id")
  3: optional list<ThriftConsumerPrivilege> privileges (wire_name="privileges")
} (
  primary_key="id"
  mongo_identifier="rogue_mongo"
  mongo_collection="oauthconsumers"
)
