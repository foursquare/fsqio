// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.common.concurrent.test

import com.twitter.util.{Await, Future, FuturePool, Return, Throw}
import io.fsq.common.concurrent.Futures
import io.fsq.common.scala.Identity._
import java.util.concurrent.{CountDownLatch, CyclicBarrier}
import org.junit.{Assert => A, Test}

class FuturesTest {
  @Test
  def testWhere(): Unit = {
    A.assertEquals(None, Await.result(Futures.where(false, Future.exception(new Exception("Shouldn't be executed")))))
    A.assertEquals(None, Await.result(Futures.where(false, Future { A.fail("should not be called") })))
    A.assertEquals(None, Await.result(Futures.where(false, Future.value(4))))
    A.assertEquals(Some(4), Await.result(Futures.where(true, Future.value(4))))
  }

  @Test
  def testGroupedCollect(): Unit = {
    val params = Vector(1, 2, 3, 4, 5, 6, 7, 8)
    val results = Vector("1", "2", "3", "4", "5", "6", "7", "8")

    for {
      size <- Vector(1, 3, 7, 7, 8, 9, 20)
    } {
      val whoops = new Exception("whoops")
      def execute(i: Int) = Future.value(i.toString)
      def throwsOn5(i: Int) = if (i == 5) Future.exception(whoops) else execute(i)

      A.assertEquals(results, Await.result(Futures.groupedCollect(params, size)(execute)))
      A.assertEquals(results.take(4), Await.result(Futures.groupedCollect(params.take(4), size)(throwsOn5)))
      try {
        Await.result(Futures.groupedCollect(params, size)(throwsOn5))
        A.fail("the closure should have thrown an exception")
      } catch {
        case e: Exception =>
          A.assertEquals(whoops, e)
      }

      A.assertEquals(results.map(Return(_)), Await.result(Futures.groupedTry(params, size)(execute)))
      A.assertEquals(results.take(4).map(Return(_)), Await.result(Futures.groupedTry(params.take(4), size)(throwsOn5)))
      A.assertEquals(
        (results.take(4).map(Return(_)) ++ Vector(Throw(whoops)) ++ results.drop(5).map(Return(_))),
        Await.result(Futures.groupedTry(params, size)(throwsOn5))
      )

      var executes = 0
      def executeWithSideEffects(i: Int) = { executes += 1; execute(i).unit }

      Await.result(Futures.groupedExecute(params, size)(executeWithSideEffects))
      A.assertEquals(params.length, executes)

      var throwsOn5s = 0
      def throwsOn5WithSideEffects(i: Int) = { throwsOn5s += 1; throwsOn5(i).unit }

      try {
        Await.result(Futures.groupedExecute(params, size)(throwsOn5WithSideEffects))
        A.fail("the closure should have thrown an exception")
      } catch {
        case e: Exception =>
          A.assertEquals(whoops, e)
          A.assertEquals(params.length, throwsOn5s)
      }
    }
  }

  @Test
  def testConcurrentGroupedCollect(): Unit = {
    val params = Vector(1, 2, 3, 4, 5, 6, 7, 8)
    val concurrency = 3

    val concurrencyLatch = new CountDownLatch(concurrency)
    val extraConcurrencyLatch = new CountDownLatch(concurrency + 1)
    val runLatch = new CountDownLatch(1)
    val successLatch = new CountDownLatch(params.size - 1)

    val blockingParam = params(0)
    val blockingBarrier = new CyclicBarrier(2)

    val completedF = Futures.groupedExecute(params, concurrency)(param => {
      FuturePool
        .interruptibleUnboundedPool({
          extraConcurrencyLatch.countDown()
          concurrencyLatch.countDown()
          if (param =? blockingParam) {
            blockingBarrier.await()
          }
          runLatch.await()
        })
        .onSuccess(_ => {
          successLatch.countDown()
        })
        .unit
    })

    // verify expected concurrency
    concurrencyLatch.await()
    A.assertEquals(extraConcurrencyLatch.getCount, 1)

    // verify windowing behavior: one Future blocks but the others are all able to execute
    runLatch.countDown()
    successLatch.await()
    A.assertEquals(blockingBarrier.getNumberWaiting, 1)

    blockingBarrier.await()
    Await.result(completedF)
  }

  @Test
  def testGroupedCollectMap(): Unit = {
    val params = Map("A" -> 1, "B" -> 2, "C" -> 3, "D" -> 4, "E" -> 5)
    val results = Map("A" -> "1", "B" -> "2", "C" -> "3", "D" -> "4", "E" -> "5")

    for {
      size <- Vector(1, 3, 7, 7, 8, 9, 20)
    } {
      val whoops = new Exception("whoops")
      def execute(i: Int) = Future.value(i.toString)
      def throwsOn5(i: Int) = if (i == 5) Future.exception(whoops) else execute(i)

      A.assertEquals(results, Await.result(Futures.groupedCollect(params, size)(execute)))
      try {
        Await.result(Futures.groupedCollect(params, size)(throwsOn5))
        A.fail("the closure should have thrown an exception")
      } catch {
        case e: Exception =>
          A.assertEquals(whoops, e)
      }
    }
  }
}
