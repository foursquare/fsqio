namespace java io.fsq.twofishes.model.gen

include "io/fsq/twofishes/types.thrift"
include "io/fsq/twofishes/geocoder.thrift"

struct ThriftNameIndex {
  1: optional types.ThriftObjectId id (wire_name="_id")
  2: optional string name
  3: optional i64 fid
  4: optional string cc
  5: optional i32 pop
  6: optional geocoder.YahooWoeType woeType
  7: optional i32 flags
  8: optional string lang
  9: optional bool excludeFromPrefixIndex
} (
  primary_key="id"
  mongo_collection="name_index"
  mongo_identifier="geocoder"
  generate_proxy="1"
)
