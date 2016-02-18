// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.common.concurrent.test

import com.twitter.util.{Await, Future, Return, Throw}
import io.fsq.common.concurrent.Futures
import org.junit.Test
import org.specs.SpecsMatchers
import org.specs.mock.Mockito

class FuturesTest extends SpecsMatchers with Mockito {
  @Test
  def testWhere() {
    Await.result(Futures.where(false, Future.exception(new Exception("Shouldn't be executed")))) must_== None
    Await.result(Futures.where(false, Future { true must_== false })) must_== None
    Await.result(Futures.where(false, Future.value(4))) must_== None
    Await.result(Futures.where(true, Future.value(4))) must_== Some(4)
  }

  @Test
  def testGroupedCollect() {
    val params = Vector(1,2,3,4,5,6,7,8)
    val results = Vector("1","2","3","4","5","6","7","8")

    for {
      size <- Vector(1, 3, 7, 7, 8, 9, 20)
    } {
      val whoops = new Exception("whoops")
      def execute(i: Int) = Future.value(i.toString)
      def throwsOn5(i: Int) = if (i == 5) Future.exception(whoops) else execute(i)

      Await.result(Futures.groupedCollect(params, size)(execute)) must_== results
      Await.result(Futures.groupedCollect(params.take(4), size)(throwsOn5)) must_== results.take(4)
      try {
        Await.result(Futures.groupedCollect(params, size)(throwsOn5))
        1 must_== 2
      } catch {
        case e: Exception => e must_== whoops
      }

      Await.result(Futures.groupedTry(params, size)(execute)) must_== results.map(Return(_))
      Await.result(Futures.groupedTry(params.take(4), size)(throwsOn5)) must_== results.take(4).map(Return(_))
      Await.result(Futures.groupedTry(params, size)(throwsOn5)) must_==
        (results.take(4).map(Return(_)) ++ Vector(Throw(whoops)) ++ results.drop(5).map(Return(_)))

      var executes = 0
      def executeWithSideEffects(i: Int) = { executes += 1; execute(i).unit }

      Await.result(Futures.groupedExecute(params, size)(executeWithSideEffects))
      executes must_== params.length

      var throwsOn5s = 0
      def throwsOn5WithSideEffects(i: Int) = { throwsOn5s += 1; throwsOn5(i).unit }

      try {
        Await.result(Futures.groupedExecute(params, size)(throwsOn5WithSideEffects))
        1 must_== 2
      } catch {
        case e: Exception =>
          e must_== whoops
          throwsOn5s must_== params.length
      }
    }
  }
}
