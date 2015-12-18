// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

namespace java io.fsq.spindle.codegen.test.gen

typedef binary (enhanced_types="bson:ObjectId") ObjectId
typedef binary (enhanced_types="bson:BSONObject") BSONObject

struct ObjectIdFields {
  1: optional ObjectId foo
  2: optional list<ObjectId> bar
  3: optional map<string, ObjectId> baz
}

struct BSONObjectFields {
  1: optional BSONObject bso
}
