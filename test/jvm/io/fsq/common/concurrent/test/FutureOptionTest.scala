// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.common.concurrent.test

import com.twitter.util.{Await, Future}
import io.fsq.common.concurrent.FutureOption
import org.junit.{Assert => A, Test}

class FutureOptionTest {
  def ensureThrowsException[U](f: () => U): Unit = {
    try {
      f()
      A.fail("FutureOption.resolve should have thrown an exception for this case")
    } catch {
      case _: Exception => // do nothing
    }
  }

  @Test
  def testBasics(): Unit = {
    def e = new Exception("")
    A.assertEquals(None, Await.result(FutureOption(Future.value(None)).resolve))
    A.assertEquals(Some(2), Await.result(FutureOption(Future.value(Some(2))).resolve))
    ensureThrowsException(() => Await.result(FutureOption(Future.exception(e)).resolve))

    A.assertEquals(None, Await.result(FutureOption(None).resolve))
    A.assertEquals(Some(2), Await.result(FutureOption(Some(Future.value(2))).resolve))
    ensureThrowsException(() => Await.result(FutureOption(Some(Future.exception(e))).resolve))

    ensureThrowsException(() => Await.result(FutureOption.exception(e).resolve))

    A.assertEquals(Some(2), Await.result(FutureOption.lift(Future.value(2)).resolve))
    ensureThrowsException(() => Await.result(FutureOption.lift(Future.exception(e)).resolve))

    A.assertEquals(Some(2), Await.result(FutureOption.lift(Some(2)).resolve))
    A.assertEquals(None, Await.result(FutureOption.lift(None).resolve))

    A.assertEquals(Some(2), Await.result(FutureOption.value(2).resolve))

    A.assertEquals(Some(4), Await.result(FutureOption.value(FutureOption.value(4)).flatten.resolve))

    var i = 0
    val f1: FutureOption[Int] = FutureOption(Future.value(Some(2)))
    f1.foreach(_ => { i = 1 })
    A.assertEquals(1, i)

    val f2: FutureOption[Int] = FutureOption.None
    f2.foreach(_ => { i = 2 })
    A.assertEquals(1, i)

    A.assertEquals(Some(2), Await.result(FutureOption.value(2).orElse(FutureOption.value(1)).resolve))
    A.assertEquals(Some(2), Await.result(FutureOption.value(2).orElse(FutureOption.None).resolve))
    A.assertEquals(Some(2), Await.result(FutureOption.value(2).orElse(FutureOption.exception(e)).resolve))
    A.assertEquals(Some(1), Await.result(FutureOption.None.orElse(FutureOption.value(1)).resolve))
    A.assertEquals(None, Await.result(FutureOption.None.orElse(FutureOption.None).resolve))
    ensureThrowsException(() => Await.result(FutureOption.None.orElse(FutureOption.exception(e)).resolve))
    ensureThrowsException(() => Await.result(FutureOption.exception(e).orElse(FutureOption.value(1)).resolve))
    ensureThrowsException(() => Await.result(FutureOption.exception(e).orElse(FutureOption.None).resolve))
    ensureThrowsException(() => Await.result(FutureOption.exception(e).orElse(FutureOption.exception(e)).resolve))

    class A {}
    class B extends A {}
    val a = new A()
    val b = new B()

    A.assertEquals(Some(b), Await.result(FutureOption.value(b).orElse(FutureOption.value(a)).resolve))
  }

  @Test
  def testFor(): Unit = {
    A.assertEquals(Some(2), Await.result((for {
      value <- FutureOption.value(2)
    } yield value).resolve))

    A.assertEquals(Some(4), Await.result((for {
      value <- FutureOption.value(2)
      double <- FutureOption(Future { Some(value * 2) })
    } yield double).resolve))

    A.assertEquals(Some(16), Await.result((for {
      value <- FutureOption.value(2)
      double <- FutureOption(Future { Some(value * 2) })
      doubleDouble <- FutureOption(Future { Some(double * 2) })
      quadrupleDouble <- FutureOption(Future { Some(doubleDouble * 2) })
    } yield quadrupleDouble).resolve))

    A.assertEquals(None, Await.result((for {
      value <- FutureOption.value(2)
      double <- FutureOption(Future.None): FutureOption[Int]
    } yield double).resolve))

    A.assertEquals(Some(4), Await.result((for {
      value <- FutureOption.value(2)
      double = value * 2
    } yield double).resolve))

    A.assertEquals(Some(2), Await.result((for {
      value <- FutureOption.value(2)
      if true
    } yield value).resolve))

    A.assertEquals(None, Await.result((for {
      value <- FutureOption.value(2)
      if false
    } yield value).resolve))

    A.assertEquals(Some(8), Await.result((for {
      value <- FutureOption.value(2)
      double = value * 2
      doubleDouble <- FutureOption(Future { Some(double * 2) })
      if true
    } yield doubleDouble).resolve))

    var i: Int = 0

    for {
      value <- FutureOption.value(2)
    } {
      i = 1
    }

    A.assertEquals(1, i)

    for {
      value <- FutureOption.apply(None): FutureOption[Int]
    } {
      i = 2
    }

    A.assertEquals(1, i)
  }
}
