// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.spindle.test

import com.mongodb.{MongoClient, ServerAddress}
import io.fsq.rogue.Rogue._
import io.fsq.rogue.spindle.{SpindleDBCollectionFactory, SpindleDatabaseService, SpindleQuery}
import io.fsq.rogue.spindle.test.gen.TestStruct
import io.fsq.spindle.runtime.UntypedMetaRecord
import org.junit.Assert._
import org.junit.Test

class TestSpindleDBService {
  @Test
  def testSimpleStruct {
    val MongoPort = Option(System.getenv("MONGO_PORT")).map(_.toInt).getOrElse(27017)
    val mongo = new MongoClient(new ServerAddress("localhost", MongoPort))

    val dbService = new SpindleDatabaseService(
      new SpindleDBCollectionFactory {
        override def getPrimaryDB(meta: UntypedMetaRecord) = mongo.getDB("test")
        override def indexCache = None
      }
    )

    val record = TestStruct.newBuilder
      .id(1)
      .info("hi")
      .result()

    dbService.save(record)

    val q = SpindleQuery(TestStruct).where(_.id eqs 1)

    assertEquals("query string", "db.test_structs.find({ \"_id\" : 1})", q.toString)

    val res = dbService.fetch(q)
    assertEquals("result length", 1, res.length)
    assertEquals("result id ", 1, res.head.idOrNull)
    assertEquals("result info", "hi", res.head.infoOrNull)

    // delete the record
    dbService.bulkDelete_!!(q)

    // ensure the record no longer exists
    assertEquals("result length post-delete", 0, dbService.fetch(q).length)
  }
}
