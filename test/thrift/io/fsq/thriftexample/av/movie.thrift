namespace java io.fsq.thriftexample.av.gen

include "io/fsq/thriftexample/talent/actor.thrift"
include "io/fsq/thriftexample/talent/crewmember.thrift"

typedef crewmember.CrewMember CrewMember
typedef binary (enhanced_types="bson:ObjectId") ObjectId
typedef ObjectId MovieId (new_type="true")
typedef i64 MinutesId (new_type="true")

struct Movie {
  5: optional MovieId id (wire_name="id", builder_required="true")
  1: required string name (wire_name="name", builder_required="true")
  2: required MinutesId lengthMinutes
  3: optional map<string, actor.Actor> cast  // E.g., "Austin Powers" -> Mike Myers
  4: optional list<CrewMember> crew
  6: optional ObjectId remakeOf
} (
  primary_key="id"
  foreign_key="remakeOf"
  index="id: asc"
  index="name: asc, lengthMinutes: desc"
  mongo_collection="movies"
  mongo_identifier="fake"
)

exception MovieException {
  1: optional list<string> problems
}
