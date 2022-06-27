// Copyright 2022 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.rogue.testlib

import com.mongodb.{BasicDBObject, WriteConcern}
import com.mongodb.client.{MongoCollection, MongoDatabase}
import io.fsq.rogue.QueryOptimizer
import io.fsq.rogue.adapter.{BlockingMongoClientAdapter, BlockingResult}
import io.fsq.rogue.connection.MongoIdentifier
import io.fsq.rogue.connection.testlib.RogueMongoTest
import io.fsq.rogue.query.QueryExecutor
import io.fsq.rogue.util.DefaultQueryUtilities
import io.fsq.spindle.rogue.adapter.SpindleMongoCollectionFactory
import io.fsq.spindle.rogue.query.SpindleRogueSerializer
import io.fsq.spindle.runtime.{UntypedMetaRecord, UntypedRecord}
import org.junit.Before

object SpindleMongoTest {
  val dbName = "spindle"
  val mongoIdentifier = MongoIdentifier("rogue_mongo")

  val queryOptimizer = new QueryOptimizer
  val serializer = new SpindleRogueSerializer
}

/** An extension of the RogueMongoTest harness for use by Spindle's own tests. */
trait SpindleMongoTest extends RogueMongoTest with BlockingResult.Implicits {

  @Before
  override def initClientManagers(): Unit = {
    blockingClientManager.defineDb(
      SpindleMongoTest.mongoIdentifier,
      () => blockingMongoClient,
      SpindleMongoTest.dbName
    )
  }

  lazy val queryExecutor: QueryExecutor[
    MongoDatabase,
    MongoCollection,
    Object,
    BasicDBObject,
    UntypedMetaRecord,
    UntypedRecord,
    BlockingResult
  ] = {
    val collectionFactory = new SpindleMongoCollectionFactory(blockingClientManager)
    val clientAdapter = new BlockingMongoClientAdapter(
      collectionFactory,
      new DefaultQueryUtilities[BlockingResult]
    )

    new QueryExecutor(
      clientAdapter,
      SpindleMongoTest.queryOptimizer,
      SpindleMongoTest.serializer
    ) {
      override def defaultWriteConcern: WriteConcern = WriteConcern.W1
    }
  }
}
