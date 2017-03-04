// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.connection.testlib.test

import io.fsq.rogue.connection.MongoIdentifier
import io.fsq.rogue.connection.testlib.RogueMongoTest
import java.util.concurrent.atomic.AtomicReference
import org.junit.{Before, Test}
import org.specs2.matcher.JUnitMustMatchers


object HarnessTest {
  val dbName = "test"
  val mongoIdentifier = MongoIdentifier("test")

  private[test] val dbNameCache = new AtomicReference[String]
}

/** Tests ensuring the thread safety of the MongoTest harness. */
class HarnessTest extends RogueMongoTest with JUnitMustMatchers {

  @Before
  override def initClientManagers(): Unit = {
    asyncClientManager.defineDb(
      HarnessTest.mongoIdentifier,
      asyncMongoClient,
      HarnessTest.dbName
    )
    blockingClientManager.defineDb(
      HarnessTest.mongoIdentifier,
      blockingMongoClient,
      HarnessTest.dbName
    )
  }

  /* Ensure async and blocking clients both use the same db. */
  @Test
  def clientConsistencyTest(): Unit = {
    val asyncDbName = asyncClientManager.use(HarnessTest.mongoIdentifier)(_.getName)
    val blockingDbName = blockingClientManager.use(HarnessTest.mongoIdentifier)(_.getName)
    asyncDbName must_== blockingDbName
  }

  /* The way this works is a bit subtle. Essentially, we run two test methods which each
   * race to read and update an atomic reference with the name of their test db and
   * compare it with the previous value. Regardless of the order in which this happens,
   * each method will always find a different db name than the one it's using if the
   * MongoTest db name mangling is working correctly. If not, the second test method to
   * complete the atomic read/update will find the duplicate db name and fail.
   */
  private def unusedDbNameCheck(): Unit = {
    asyncClientManager.use(HarnessTest.mongoIdentifier)(db => {
      HarnessTest.dbNameCache.getAndSet(db.getName) must_!= db.getName
    })
  }

  @Test
  def unusedDbNameTest1(): Unit = unusedDbNameCheck()

  @Test
  def unusedDbNameTest2(): Unit = unusedDbNameCheck()
}

