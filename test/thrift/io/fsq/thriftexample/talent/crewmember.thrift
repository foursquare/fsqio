namespace java io.fsq.thriftexample.talent.gen

include "io/fsq/thriftexample/people/person.thrift"

struct CrewMember {
  1: required person.Person details
  2: required list<string> credits  // E.g., "2nd Assistant Director"
}
