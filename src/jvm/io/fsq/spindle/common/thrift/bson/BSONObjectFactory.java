//  Copyright 2011 Foursquare Labs Inc. All Rights Reserved

package io.fsq.spindle.common.thrift.bson;

import org.bson.BSONObject;

// A level of indirection for the creation of BSONObject instances. This allows us to decouple the
// Thrift <--> BSON code from MongoDB-specific subtypes, and operate at the level of generic BSON.
interface BSONObjectFactory<B extends BSONObject> {
  B create();
}
