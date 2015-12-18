namespace java io.fsq.spindle.thriftexample.av.gen

include "io/fsq/spindle/thriftexample/talent/actor.thrift"

typedef actor.Actor Actor

struct TvShow {
  1: required string name
  2: optional string description
}

struct TvShowSeason {
  1: required TvShow tvShow
  2: required i32 seasonNumber
}

struct TvShowEpisode {
  1: required TvShowSeason season
  2: required string name
  3: optional string description
  4: required i32 lengthMinutes
  5: optional byte numCommercialBreaks
  6: optional list<Actor> cast
}

struct TvSpecial {
  1: required string name
  2: optional string description
  3: required i32 lengthMinutes
  4: optional byte numCommercialBreaks
}
