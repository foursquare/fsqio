namespace java io.fsq.twofishes.model.gen

include "io/fsq/twofishes/types.thrift"

struct ThriftPolygonIndex {
  1: optional types.ThriftObjectId id (wire_name="_id")
  2: optional binary polygon
  3: optional string source
} (
  primary_key="id"
  mongo_collection="polygon_index"
  mongo_identifier="geocoder"
  generate_proxy="1"
)
