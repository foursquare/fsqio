namespace java io.fsq.twofishes.model.gen

include "io/fsq/twofishes/types.thrift"
include "io/fsq/twofishes/geocoder.thrift"

struct ThriftDisplayName {
  1: optional types.ThriftObjectId id (wire_name="_id")
  2: optional string lang
  3: optional string name
  4: optional i32 flags
} (
  generate_proxy="1"
)

struct ThriftPoint {
  1: optional double lat
  2: optional double lng
} (
  generate_proxy="1"
)

struct ThriftBoundingBox {
  1: optional ThriftPoint ne
  2: optional ThriftPoint sw
} (
  generate_proxy="1"
)

struct ThriftGeocodeRecord {
  1: optional i64 id (wire_name="_id")
  2: optional list<string> names
  3: optional string cc
  4: optional geocoder.YahooWoeType woeType (wire_name="_woeType")
  5: optional double lat
  6: optional double lng
  7: optional list<ThriftDisplayName> displayNames
  8: optional list<i64> parents
  9: optional i32 population
  10: optional i32 boost
  11: optional ThriftBoundingBox boundingBox (wire_name="boundingbox")
  12: optional ThriftBoundingBox displayBounds
  13: optional bool canGeocode
  14: optional string slug
  15: optional binary polygon
  16: optional bool hasPoly
  17: optional binary rawAttributes (wire_name="attributes")
  18: optional list<i64> extraRelations
  19: optional types.ThriftObjectId polyId
  20: optional list<i64> ids
  21: optional string polygonSource
} (
  primary_key="id"
  mongo_collection="features"
  mongo_identifier="geocoder"
  generate_proxy="1"
)
