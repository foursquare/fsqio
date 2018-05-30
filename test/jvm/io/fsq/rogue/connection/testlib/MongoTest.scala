// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.connection.testlib

import com.mongodb.{MongoClient => BlockingMongoClient}
import com.mongodb.async.SingleResultCallback
import com.mongodb.async.client.{MongoClient => AsyncMongoClient}
import io.fsq.rogue.connection.{AsyncMongoClientManager, BlockingMongoClientManager, MongoIdentifier}
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import org.junit.{After, Before}


object MongoTest {
  private[testlib] val dbIdCounter = new AtomicInteger(0)
}

trait MongoTest {

  private val dbId = MongoTest.dbIdCounter.incrementAndGet()
  private def getDbName(dbName: String): String = s"$dbName-$dbId"

  val asyncClientManager = new AsyncMongoClientManager {
    override def defineDb(
      name: MongoIdentifier,
      client: AsyncMongoClient,
      dbName: String
    ): Option[(AsyncMongoClient, String)] = {
      super.defineDb(name, client, getDbName(dbName))
    }
  }

  val blockingClientManager = new BlockingMongoClientManager {
    override def defineDb(
      name: MongoIdentifier,
      client: BlockingMongoClient,
      dbName: String
    ): Option[(BlockingMongoClient, String)] = {
      super.defineDb(name, client, getDbName(dbName))
    }
  }

  @Before
  def initClientManagers(): Unit

  @After
  def dropTestDbs(): Unit = {
    val asyncConnectionIds = asyncClientManager.getConnectionIds
    val asyncDropLatch = new CountDownLatch(asyncConnectionIds.size)
    val asyncDropCallback = new SingleResultCallback[Void] {
      override def onResult(result: Void, throwable: Throwable): Unit = {
        asyncDropLatch.countDown()
      }
    }
    asyncConnectionIds.foreach(id => {
      asyncClientManager.use(id)(_.drop(asyncDropCallback))
    })

    blockingClientManager.getConnectionIds.foreach(id => {
      blockingClientManager.use(id)(_.drop())
    })

    asyncDropLatch.await()
  }
}
