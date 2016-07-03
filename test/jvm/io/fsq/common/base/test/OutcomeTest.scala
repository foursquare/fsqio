// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.common.base.test

import io.fsq.common.base.{Failure, FailureProjection, Outcome, Success}
import org.junit.{Assert, Test}
import org.specs.SpecsMatchers

class OutcomeTest extends SpecsMatchers {
  @Test
  def testSuccess() {
    val value = "foo"

    val x: Outcome[String, String] = Success(value)
    x.isSuccess must beTrue
    x.isFailure must beFalse

    x.either match {
      case Right(v) => v must_== value
      case _ => Assert.fail("Success.either was not a Right")
    }

    x.toOption match {
      case Some(v) => v must_== value
      case _ => Assert.fail("Sucess.toOption was not a Some")
    }

    x.fold({ v => v must_== value }, { v => Assert.fail("Success.fold must not invoke onFailure") })

    x.exists(_ == value) must beTrue
    x.exists(_ == (value + value)) must beFalse

    x.forall(_ == value) must beTrue
    x.forall(_ == (value + value)) must beFalse

    x.map(_.length) must_== Success(value.length)
    x.flatMap(v => Success(v.length)) must_== Success(value.length)

    var i = 0
    x.foreach { v => i = v.length }
    i must_== value.length

    x.rescue(x => Success(x + "bar")) must_== Success("foo")
    x.rescue(x => Failure(x + "bar")) must_== Success("foo")

    x.filter(x => x == "foo", "ugh") must_== Success("foo")
    x.filter(x => x == "bar", "ugh") must_== Failure("ugh")
  }

  @Test
  def testFailure() {
    val value = "foo"

    val x: Outcome[String, String] = Failure(value)
    x.isSuccess must beFalse
    x.isFailure must beTrue

    x.either match {
      case Left(v) => v must_== value
      case _ => Assert.fail("Failure.either was not a Left")
    }

    x.toOption match {
      case None =>
      case _ => Assert.fail("Failure.toOption was not a None")
    }

    x.fold({ v => Assert.fail("Failure.fold must not invoke onSuccess") }, { v => v must_== value })

    x.exists(_ == value) must beFalse
    x.exists(_ == (value + value)) must beFalse

    x.forall(_ == value) must beTrue
    x.forall(_ == (value + value)) must beTrue

    x.map(_.length) must_== x
    x.flatMap(v => Success(v.length)) must_== x
    x.foreach { x => Assert.fail("Failure.foreach must not invoke the closure") }

    x.rescue(x => Success(x + "bar")) must_== Success("foobar")
    x.rescue(x => Failure(x + "bar")) must_== Failure("foobar")

    x.filter(x => x == "foo", "ugh") must_== Failure("foo")
    x.filter(x => x == "bar", "ugh") must_== Failure("foo")
  }

  @Test
  def testFailureProjectionOfSuccess() {
    val value = "foo"

    val sp = Success[String, String](value).failure

    sp.toOption match {
      case None =>
      case _ => Assert.fail("Success.failure.toOption must be None")
    }

    sp.exists(_ == value) must beFalse
    sp.exists(_ == (value + value)) must beFalse

    sp.forall(_ == value) must beTrue
    sp.forall(_ == (value + value)) must beTrue

    sp.map(_.length) must haveClass[FailureProjection[String, Int]]
    sp.flatMap(v => Some(v.length)) must beNone
    sp.foreach { x => Assert.fail("FailureProject(Success).foreach must not invoke the closure") }
  }

  @Test
  def testFailureProjectionOfFailure() {
    val value = "foo"

    val fp = Failure[String, String](value).failure

    fp.toOption match {
      case Some(v) => v must_== value
      case _ => Assert.fail("Failure.failure.toOption must be Some")
    }

    fp.exists(_ == value) must beTrue
    fp.exists(_ == (value + value)) must beFalse

    fp.forall(_ == value) must beTrue
    fp.forall(_ == (value + value)) must beFalse

    fp.map(_.length) match {
      case FailureProjection(Failure(v)) => v must_== value.length
      case _ => Assert.fail("Failure.failure.map did not work")
    }

    fp.flatMap(v => Some(v.length)) must beSome(value.length)

    var i = 0
    fp.foreach { v => i = v.length }
    i must_== value.length
  }

  @Test
  def testFlatten() {
    (Success(Success("foo"))).flatten must_== Success("foo")
    (Success(Failure("foo"))).flatten must_== Failure("foo")
    (Failure("foo")).flatten must_== Failure("foo")
  }
}
