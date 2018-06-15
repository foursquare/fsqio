// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.common.testing.matchers.test

import io.fsq.common.testing.AssertException
import io.fsq.common.testing.matchers.{FoursquareMatchers => FM}
import org.junit.{Assert => A, Test}
import scala.util.matching.Regex

class FoursquareMatchersTest {
  def anyAssert: PartialFunction[Throwable, Boolean] = { case e: AssertionError => true }
  def assertWithMessage(r: Regex): PartialFunction[Throwable, Boolean] = {
    case e: AssertionError => r.findFirstMatchIn(e.getMessage.toLowerCase).isDefined
  }

  @Test
  def testIsNoneMatcher(): Unit = {
    A.assertThat((None: Option[Int]), FM.isNone[Int])
    AssertException(anyAssert)(
      A.assertThat((Some(2): Option[Int]), FM.isNone[Int])
    )
  }

  @Test
  def testExistsMatcher(): Unit = {
    A.assertThat(Some(5), FM.exists[Int](_ > 2, "_ > 2"))
    AssertException(anyAssert) {
      A.assertThat(Some(1), FM.exists[Int](_ > 2, "_ > 2"))
    }
    AssertException(assertWithMessage("expected: some\\[int\\] matching predicate: _ > 2".r)) {
      A.assertThat(None: Option[Int], FM.exists[Int](_ > 2, "_ > 2"))
    }
  }

  @Test
  def testEqualsCollectionMatcher(): Unit = {
    A.assertThat(Vector(1, 2), FM.equalsCollection(Vector(1, 2)))
    AssertException(assertWithMessage("lengths differed".r))(
      A.assertThat(Vector(1), FM.equalsCollection(Vector(1, 2)))
    )
    AssertException(assertWithMessage("index 0.*expected=2.*actual=1".r))(
      A.assertThat(Vector(1), FM.equalsCollection(Vector(2)))
    )
  }
}
