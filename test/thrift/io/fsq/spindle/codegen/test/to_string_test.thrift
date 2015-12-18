// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

namespace java io.fsq.spindle.codegen.test.gen

typedef binary (enhanced_types="bson:ObjectId") ObjectId

struct MapsWithNonStringKeys {
  1: optional map<i32, string> foo
  2: optional map<ObjectId, i32> bar
  3: optional map<KeyStruct, i32> baz
}

struct KeyStruct {
  1: optional string a
  2: optional i32 b
}
