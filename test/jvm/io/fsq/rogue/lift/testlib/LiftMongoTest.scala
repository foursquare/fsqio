// Copyright 2022 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.lift.testlib

import com.mongodb.WriteConcern
import io.fsq.common.scala.Identity._
import io.fsq.rogue.QueryOptimizer
import io.fsq.rogue.adapter.{BlockingMongoClientAdapter, BlockingResult}
import io.fsq.rogue.adapter.lift.LiftMongoCollectionFactory
import io.fsq.rogue.connection.testlib.RogueMongoTest
import io.fsq.rogue.lift.LiftRogue
import io.fsq.rogue.query.QueryExecutor
import io.fsq.rogue.query.lift.LiftRogueSerializer
import io.fsq.rogue.util.DefaultQueryUtilities
import org.junit.Before

object LiftMongoTest {
  val dbName = "lift"
  val mongoIdentifier = RogueTestMongoIdentifier

  val queryOptimizer = new QueryOptimizer
  val serializer = new LiftRogueSerializer
}

/** An extension of the RogueMongoTest harness for use by Rogue's Lift tests. */
trait LiftMongoTest extends RogueMongoTest with BlockingResult.Implicits {

  @Before
  override def initClientManagers(): Unit = {
    blockingClientManager.defineDb(
      LiftMongoTest.mongoIdentifier,
      () => blockingMongoClient,
      LiftMongoTest.dbName
    )
  }

  lazy val queryExecutor: LiftRogue.Executor = {
    val collectionFactory = new LiftMongoCollectionFactory(blockingClientManager)
    val clientAdapter = new BlockingMongoClientAdapter(
      collectionFactory,
      new DefaultQueryUtilities[BlockingResult]
    )

    new QueryExecutor(
      clientAdapter,
      LiftMongoTest.queryOptimizer,
      LiftMongoTest.serializer
    ) {
      override def defaultWriteConcern: WriteConcern = WriteConcern.W1
    }
  }

  // TODO(jacob): Get rid of ExecutableQuery and friends to remove the need for setting
  //    this global state.
  LiftRogue.synchronized {
    // if (RogueLiftTestMetaRecord.clientManager =? null) {
    //   RogueLiftTestMetaRecord.clientManager = blockingClientManager
    // }
    if (LiftRogue.executor =? null) {
      LiftRogue.executor = queryExecutor
    }
  }
}
