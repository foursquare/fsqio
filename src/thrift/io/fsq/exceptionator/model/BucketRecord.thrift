namespace java io.fsq.exceptionator.model.gen

include "io/fsq/exceptionator/types.thrift"

struct BucketRecord {
  1: optional string id (wire_name="_id")
  2: optional i32 noticeCount (wire_name="n")
  3: optional i64 firstSeen (wire_name="df")
  4: optional i64 lastSeen (wire_name="dl")
  5: optional string firstVersion (wire_name="vf")
  6: optional string lastVersion (wire_name="vl")
  7: optional list<types.ObjectId> notices (wire_name="ids")
} (
  primary_key="id"
  mongo_collection="buckets"
  mongo_identifier="exceptionator"
  generate_proxy="1"
)

struct BucketRecordHistogram {
  1: optional string id (wire_name="_id")
  2: optional map<string, i32> histogram (wire_name="h")
} (
  primary_key="id"
  mongo_collection="bucket_histograms"
  mongo_identifier="exceptionator"
  generate_proxy="1"
)
