// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.connection.testlib

import com.mongodb.{MongoClient => BlockingMongoClient}
import com.mongodb.reactivestreams.client.{MongoClient => AsyncMongoClient, Success}
import io.fsq.rogue.connection.{AsyncMongoClientManager, BlockingMongoClientManager, MongoIdentifier}
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import org.junit.{After, Before}
import org.reactivestreams.{Subscriber, Subscription}

object MongoTest {
  private[testlib] val dbIdCounter = new AtomicInteger(0)
}

trait MongoTest {

  private val dbId = MongoTest.dbIdCounter.incrementAndGet()
  private def getDbName(dbName: String): String = s"$dbName-$dbId"

  val asyncClientManager = new AsyncMongoClientManager {
    override def defineDb(
      name: MongoIdentifier,
      clientFn: () => AsyncMongoClient,
      dbName: String
    ): Unit = {
      super.defineDb(name, clientFn, getDbName(dbName))
    }
  }

  val blockingClientManager = new BlockingMongoClientManager {
    override def defineDb(
      name: MongoIdentifier,
      clientFn: () => BlockingMongoClient,
      dbName: String
    ): Unit = {
      super.defineDb(name, clientFn, getDbName(dbName))
    }
  }

  @Before
  def initClientManagers(): Unit

  @After
  def dropTestDbs(): Unit = {
    val asyncConnectionIds = asyncClientManager.getConnectionIds
    val asyncDropLatch = new CountDownLatch(asyncConnectionIds.size)
    val asyncDropCallback = new Subscriber[Success] {
      override def onSubscribe(s: Subscription): Unit = s.request(1)
      override def onError(t: Throwable): Unit = throw t
      override def onComplete(): Unit = {}
      override def onNext(t: Success): Unit = {
        asyncDropLatch.countDown()
      }
    }
    asyncConnectionIds.foreach(id => {
      asyncClientManager.use(id)(_.drop().subscribe(asyncDropCallback))
    })

    blockingClientManager.getConnectionIds.foreach(id => {
      blockingClientManager.use(id)(_.drop())
    })

    asyncDropLatch.await()
  }
}
