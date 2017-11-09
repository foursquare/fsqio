namespace java io.fsq.twofishes.model.gen

include "io/fsq/twofishes/types.thrift"

struct ThriftRevGeoIndex {
  1: optional types.ThriftObjectId id (wire_name="_id")
  2: optional i64 cellId (wire_name="cellid")
  3: optional types.ThriftObjectId polyId
  4: optional bool full
  5: optional binary geom
  6: optional list<double> point
} (
  primary_key="id"
  mongo_collection="regeo_index"
  mongo_identifier="geocoder"
  generate_proxy="1"
)
