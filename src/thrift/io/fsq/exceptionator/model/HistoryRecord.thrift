namespace java io.fsq.exceptionator.model.gen

include "io/fsq/exceptionator/types.thrift"

struct Incoming {
  1: optional i32 id (wire_name="id")
  2: optional list<string> exceptions (wire_name="excs")
  3: optional list<string> exceptionStack (wire_name="bt")
  4: optional map<string, string> session (wire_name="sess")
  5: optional map<string, string> enviroment (wire_name="env")
  6: optional string host (wire_name="h")
  7: optional string version (wire_name="v")
  8: optional i32 count (wire_name="n")
  9: optional i64 date (wire_name="d")
  10: optional list<string> tags (wire_name="tags")
  11: optional i32 timeToExpire (wire_name="ttl")
  12: optional list<string> messages (wire_name="msgs")
} (
  generate_proxy="1"
)

struct NoticeRecord {
  1: optional types.ObjectId id (wire_name="_id")
  2: optional types.DateTime expireAt (wire_name="e")
  3: optional list<string> tags (wire_name="t")
  4: optional list<string> buckets (wire_name="b")
  5: optional Incoming notice (wire_name="n")
  6: optional list<string> keywords (wire_name="kw")
} (
  primary_key="id"
  mongo_collection="notices"
  mongo_identifier="exceptionator"
  generate_proxy="1"
)

struct HistoryRecord {
  1: optional types.DateTime id (wire_name="_id")
  2: optional i32 window (wire_name="w")
  3: optional i32 totalSampled (wire_name="s")
  4: optional i32 sampleRate (wire_name="r")
  5: optional types.DateTime earliestExpiration (wire_name="e")
  6: optional list<string> buckets (wire_name="b")
  7: optional list<NoticeRecord> notices (wire_name="n")
} (
  primary_key="id"
  mongo_collection="history"
  mongo_identifier="exceptionator"
  index="id:asc,id:desc"
  generate_proxy="1"
)
