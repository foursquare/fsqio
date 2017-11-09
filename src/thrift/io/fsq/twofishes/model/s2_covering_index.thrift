namespace java io.fsq.twofishes.model.gen

include "io/fsq/twofishes/types.thrift"

struct ThriftS2CoveringIndex {
  1: optional types.ThriftObjectId id (wire_name="_id")
  2: optional list<i64> cellIds
} (
  primary_key="id"
  mongo_collection="s2_covering_index"
  mongo_identifier="geocoder"
)
