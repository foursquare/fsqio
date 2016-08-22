// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.common.base.test

import io.fsq.common.base.{Failure, FailureProjection, Outcome, Success}
import org.junit.{Assert => A, Test}

class OutcomeTest {
  @Test
  def testSuccess() {
    val value = "foo"

    val x: Outcome[String, String] = Success(value)
    A.assertTrue(x.isSuccess)
    A.assertFalse(x.isFailure)

    x.either match {
      case Right(v) => A.assertEquals(value, v)
      case _ => A.fail("Success.either was not a Right")
    }

    x.toOption match {
      case Some(v) => A.assertEquals(value, v)
      case _ => A.fail("Sucess.toOption was not a Some")
    }

    x.fold({ v => A.assertEquals(value, v) }, { v => A.fail("Success.fold must not invoke onFailure") })

    A.assertTrue(x.exists(_ == value))
    A.assertFalse(x.exists(_ == (value + value)))

    A.assertTrue(x.forall(_ == value))
    A.assertFalse(x.forall(_ == (value + value)))

    A.assertEquals(Success(value.length), x.map(_.length))
    A.assertEquals(Success(value.length), x.flatMap(v => Success(v.length)))

    var i = 0
    x.foreach { v => i = v.length }
    A.assertEquals(value.length, i)

    A.assertEquals(Success("foo"), x.rescue(x => Success(x + "bar")))
    A.assertEquals(Success("foo"), x.rescue(x => Failure(x + "bar")))

    A.assertEquals(Success("foo"), x.filter(x => x == "foo", "ugh"))
    A.assertEquals(Failure("ugh"), x.filter(x => x == "bar", "ugh"))
  }

  @Test
  def testFailure() {
    val value = "foo"

    val x: Outcome[String, String] = Failure(value)
    A.assertFalse(x.isSuccess)
    A.assertTrue(x.isFailure)

    x.either match {
      case Left(v) => A.assertEquals(value, v)
      case _ => A.fail("Failure.either was not a Left")
    }

    x.toOption match {
      case None =>
      case _ => A.fail("Failure.toOption was not a None")
    }

    x.fold({ v => A.fail("Failure.fold must not invoke onSuccess") }, { v => A.assertEquals(value, v) })

    A.assertFalse(x.exists(_ == value))
    A.assertFalse(x.exists(_ == (value + value)))

    A.assertTrue(x.forall(_ == value))
    A.assertTrue(x.forall(_ == (value + value)))

    A.assertEquals(x, x.map(_.length))
    A.assertEquals(x, x.flatMap(v => Success(v.length)))
    x.foreach { x => A.fail("Failure.foreach must not invoke the closure") }

    A.assertEquals(Success("foobar"), x.rescue(x => Success(x + "bar")))
    A.assertEquals(Failure("foobar"), x.rescue(x => Failure(x + "bar")))

    A.assertEquals(Failure("foo"), x.filter(x => x == "foo", "ugh"))
    A.assertEquals(Failure("foo"), x.filter(x => x == "bar", "ugh"))
  }

  @Test
  def testFailureProjectionOfSuccess() {
    val value = "foo"

    val sp = Success[String, String](value).failure

    sp.toOption match {
      case None =>
      case _ => A.fail("Success.failure.toOption must be None")
    }

    A.assertFalse(sp.exists(_ == value))
    A.assertFalse(sp.exists(_ == (value + value)))

    A.assertTrue(sp.forall(_ == value))
    A.assertTrue(sp.forall(_ == (value + value)))

    A.assertTrue(classOf[FailureProjection[String, Int]].isAssignableFrom(sp.map(_.length).getClass))
    A.assertTrue(sp.flatMap(v => Some(v.length)).isEmpty)
    sp.foreach { x => A.fail("FailureProject(Success).foreach must not invoke the closure") }
  }

  @Test
  def testFailureProjectionOfFailure() {
    val value = "foo"

    val fp = Failure[String, String](value).failure

    fp.toOption match {
      case Some(v) => A.assertEquals(value, v)
      case _ => A.fail("Failure.failure.toOption must be Some")
    }

    A.assertTrue(fp.exists(_ == value))
    A.assertFalse(fp.exists(_ == (value + value)))

    A.assertTrue(fp.forall(_ == value))
    A.assertFalse(fp.forall(_ == (value + value)))

    fp.map(_.length) match {
      case FailureProjection(Failure(v)) => A.assertEquals(value.length, v)
      case _ => A.fail("Failure.failure.map did not work")
    }

    A.assertEquals(Some(value.length), fp.flatMap(v => Some(v.length)))

    var i = 0
    fp.foreach { v => i = v.length }
    A.assertEquals(value.length, i)
  }

  @Test
  def testFlatten() {
    A.assertEquals(Success("foo"), (Success(Success("foo"))).flatten)
    A.assertEquals(Failure("foo"), (Success(Failure("foo"))).flatten)
    A.assertEquals(Failure("foo"), Failure("foo").flatten)
  }
}
