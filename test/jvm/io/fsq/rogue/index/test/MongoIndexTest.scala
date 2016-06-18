// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.index.test

import io.fsq.rogue.index.{Asc, Desc, Hashed, IndexBuilder, SpindleIndexSubField}
import io.fsq.rogue.spindle.test.gen.{ThriftVenue, ThriftVenueClaimBson, ThriftVenueMeta}
import org.junit.Test
import org.specs2.matcher.JUnitMustMatchers

class MongoIndexTest extends JUnitMustMatchers {
  @Test
  def testIndexGeneration(): Unit = {
    IndexBuilder[ThriftVenueMeta](ThriftVenue).index(_.id, Asc).toString must_== "_id:1"
    IndexBuilder[ThriftVenueMeta](ThriftVenue).index(_.id, Hashed).toString must_== "_id:hashed"
    IndexBuilder[ThriftVenueMeta](ThriftVenue).index(_.userid, Asc, _.id, Desc).toString must_== "userid:1, _id:-1"
    IndexBuilder[ThriftVenueMeta](ThriftVenue).index(_.status, Asc,
      _ => new SpindleIndexSubField(ThriftVenue.lastClaim, ThriftVenueClaimBson.userid),
    Asc).toString must_== "status:1, last_claim.uid:1"
  }
}