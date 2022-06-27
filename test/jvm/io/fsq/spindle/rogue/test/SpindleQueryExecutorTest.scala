// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.rogue.test

import io.fsq.rogue.Rogue._
import io.fsq.spindle.rogue.SpindleQuery
import io.fsq.spindle.rogue.testlib.SpindleMongoTest
import io.fsq.spindle.rogue.testlib.gen.TestStruct
import org.junit.Assert._
import org.junit.Test

class SpindleQueryExecutorTest extends SpindleMongoTest {
  @Test
  def testSimpleStruct {
    val record = TestStruct.newBuilder
      .id(1)
      .info("hi")
      .result()

    queryExecutor.save(record)

    val q = SpindleQuery(TestStruct).where(_.id eqs 1)

    assertEquals("query string", "db.test_structs.find({\"_id\": 1})", q.toString)

    val res = queryExecutor.fetch(q)
    assertEquals("result length", 1, res.length)
    assertEquals("result id ", 1, res.head.idOrNull)
    assertEquals("result info", "hi", res.head.infoOrNull)

    // delete the record
    queryExecutor.bulkDelete_!!(q)

    // ensure the record no longer exists
    assertEquals("result length post-delete", 0, queryExecutor.fetch(q).length)
  }
}
