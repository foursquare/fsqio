namespace java io.fsq.twofishes.gen

include "io/fsq/twofishes/geocoder.thrift"

struct ChildEntry {
  1: optional string name
  2: optional string slug
  3: optional string id
  4: optional geocoder.YahooWoeType woeType
  5: optional bool isInternational
}

struct ChildEntries {
  1: optional list<ChildEntry> entries
}
