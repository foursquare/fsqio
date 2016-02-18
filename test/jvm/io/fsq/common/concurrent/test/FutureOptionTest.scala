// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.common.concurrent.test

import com.twitter.util.{Await, Future}
import io.fsq.common.concurrent.FutureOption
import org.junit.Test
import org.specs.SpecsMatchers

class FutureOptionTest extends SpecsMatchers  {
  @Test
  def testBasics: Unit = {
    def e = new Exception("")
    Await.result(FutureOption(Future.value(None)).resolve) must_== None
    Await.result(FutureOption(Future.value(Some(2))).resolve) must_== Some(2)
    Await.result(FutureOption(Future.exception(e)).resolve) must throwA[Exception]

    Await.result(FutureOption(None).resolve) must_== None
    Await.result(FutureOption(Some(Future.value(2))).resolve) must_== Some(2)
    Await.result(FutureOption(Some(Future.exception(e))).resolve) must throwA[Exception]

    Await.result(FutureOption.exception(e).resolve) must throwA[Exception]

    Await.result(FutureOption.lift(Future.value(2)).resolve) must_== Some(2)
    Await.result(FutureOption.lift(Future.exception(e)).resolve) must throwA[Exception]

    Await.result(FutureOption.lift(Some(2)).resolve) must_== Some(2)
    Await.result(FutureOption.lift(None).resolve) must_== None

    Await.result(FutureOption.value(2).resolve) must_== Some(2)

    Await.result(FutureOption.value(FutureOption.value(4)).flatten.resolve) must_== Some(4)

    var i = 0
    val f1: FutureOption[Int] = FutureOption(Future.value(Some(2)))
    f1.foreach(_ => { i = 1 })
    i must_== 1

    val f2: FutureOption[Int] = FutureOption.None
    f2.foreach(_ => { i = 2 })
    i must_== 1

    Await.result(FutureOption.value(2).orElse(FutureOption.value(1)).resolve) must_== Some(2)
    Await.result(FutureOption.value(2).orElse(FutureOption.None).resolve) must_== Some(2)
    Await.result(FutureOption.value(2).orElse(FutureOption.exception(e)).resolve) must_== Some(2)
    Await.result(FutureOption.None.orElse(FutureOption.value(1)).resolve) must_== Some(1)
    Await.result(FutureOption.None.orElse(FutureOption.None).resolve) must_== None
    Await.result(FutureOption.None.orElse(FutureOption.exception(e)).resolve) must throwA[Exception]
    Await.result(FutureOption.exception(e).orElse(FutureOption.value(1)).resolve) must throwA[Exception]
    Await.result(FutureOption.exception(e).orElse(FutureOption.None).resolve) must throwA[Exception]
    Await.result(FutureOption.exception(e).orElse(FutureOption.exception(e)).resolve) must throwA[Exception]

    class A {}
    class B extends A {}
    val a = new A()
    val b = new B()

    Await.result(FutureOption.value(b).orElse(FutureOption.value(a)).resolve) must_== Some(b)
  }

  @Test
  def testFor: Unit = {
    Await.result((for {
      value <- FutureOption.value(2)
    } yield value).resolve) must_== Some(2)

    Await.result((for {
      value <- FutureOption.value(2)
      double <- FutureOption(Future { Some(value * 2) })
    } yield double).resolve) must_== Some(4)

    Await.result((for {
      value <- FutureOption.value(2)
      double <- FutureOption(Future { Some(value * 2) })
      doubleDouble <- FutureOption(Future { Some(double * 2) })
      quadrupleDouble <- FutureOption(Future { Some(doubleDouble * 2) })
    } yield quadrupleDouble).resolve) must_== Some(16)

    Await.result((for {
      value <- FutureOption.value(2)
      double <- FutureOption(Future.None): FutureOption[Int]
    } yield double).resolve) must_== None

    Await.result((for {
      value <- FutureOption.value(2)
      double = value * 2
    } yield double).resolve) must_== Some(4)

    Await.result((for {
      value <- FutureOption.value(2)
      if true
    } yield value).resolve) must_== Some(2)

    Await.result((for {
      value <- FutureOption.value(2)
      if false
    } yield value).resolve) must_== None

    Await.result((for {
      value <- FutureOption.value(2)
      double = value * 2
      doubleDouble <- FutureOption(Future { Some(double * 2) })
      if true
    } yield doubleDouble).resolve) must_== Some(8)

    var i: Int = 0

    for {
      value <- FutureOption.value(2)
    } {
      i = 1
    }

    i must_== 1

    for {
      value <- FutureOption.apply(None): FutureOption[Int]
    } {
      i = 2
    }

    i must_== 1
  }
}

