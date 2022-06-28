// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.rogue.test

import io.fsq.rogue.index.{Asc, Desc, Hashed, MongoIndex}
import io.fsq.spindle.rogue.SpindleIndexSubField
import io.fsq.spindle.rogue.testlib.gen.{ThriftVenue, ThriftVenueClaimBson, ThriftVenueMeta}
import org.junit.Test
import org.specs2.matcher.JUnitMustMatchers

class SpindleMongoIndexTest extends JUnitMustMatchers {
  @Test
  def testIndexGeneration(): Unit = {
    MongoIndex
      .builder[ThriftVenueMeta](ThriftVenue)
      .index(
        _.id,
        Asc
      )
      .toString must_== "_id:1"
    MongoIndex
      .builder[ThriftVenueMeta](ThriftVenue)
      .index(
        _.id,
        Hashed
      )
      .toString must_== "_id:hashed"
    MongoIndex
      .builder[ThriftVenueMeta](ThriftVenue)
      .index(
        _.userid,
        Asc,
        _.id,
        Desc
      )
      .toString must_== "userid:1, _id:-1"
    MongoIndex
      .builder[ThriftVenueMeta](ThriftVenue)
      .index(
        _.status,
        Asc,
        _ => new SpindleIndexSubField(ThriftVenue.lastClaim, ThriftVenueClaimBson.userid),
        Asc
      )
      .toString must_== "status:1, last_claim.uid:1"
  }
}
