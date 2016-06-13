namespace java io.fsq.thriftexample.gen

include "io/fsq/thriftexample/av/tv.thrift"
include "io/fsq/thriftexample/av/movie.thrift"

typedef string DateTime // String in the format YYYY-MM-DD HH:MM:SS

// newtype test
typedef binary (enhanced_types="bson:ObjectId") ObjectId
typedef ObjectId MyObjectId (new_type="true")
typedef string MyString (new_type="true")
typedef i64 MyLong (new_type="true")

union Content {
  1: optional tv.TvShowEpisode show
  2: optional movie.Movie movie
}

struct TvListingEntry {
  1: DateTime startTime (wire_name="st")
  2: DateTime endTime (wire_name="et")
  3: Content content
}

typedef list<TvListingEntry> TvListing
