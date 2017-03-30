// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.connection.testlib

import com.mongodb.{MongoClient => BlockingMongoClient}
import com.mongodb.async.SingleResultCallback
import com.mongodb.async.client.{MongoClient => AsyncMongoClient, MongoDatabase => AsyncMongoDatabase}
import com.mongodb.client.{MongoDatabase => BlockingMongoDatabase}
import io.fsq.rogue.connection.{AsyncMongoClientManager, BlockingMongoClientManager}
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import org.junit.{After, Before}


object MongoTest {
  private[testlib] val dbIdCounter = new AtomicInteger(0)
}

trait MongoTest {

  private val dbId = MongoTest.dbIdCounter.incrementAndGet()

  val asyncClientManager = new AsyncMongoClientManager {
    override protected def getDatabase(client: AsyncMongoClient, name: String): AsyncMongoDatabase = {
      client.getDatabase(s"$name-$dbId")
    }
  }

  val blockingClientManager = new BlockingMongoClientManager {
    override protected def getDatabase(client: BlockingMongoClient, name: String): BlockingMongoDatabase = {
      client.getDatabase(s"$name-$dbId")
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

