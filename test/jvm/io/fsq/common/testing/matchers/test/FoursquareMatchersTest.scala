// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.common.testing.matchers.test

import io.fsq.common.testing.AssertException
import io.fsq.common.testing.matchers.{FoursquareMatchers => FM}
import org.hamcrest.MatcherAssert
import org.junit.Test
import scala.util.matching.Regex

class FoursquareMatchersTest {
  def anyAssert: PartialFunction[Throwable, Boolean] = { case e: AssertionError => true }
  def assertWithMessage(r: Regex): PartialFunction[Throwable, Boolean] = {
    case e: AssertionError => r.findFirstMatchIn(e.getMessage.toLowerCase).isDefined
  }

  @Test
  def testIsNoneMatcher(): Unit = {
    MatcherAssert.assertThat((None: Option[Int]), FM.isNone[Int])
    AssertException(anyAssert)(
      MatcherAssert.assertThat((Some(2): Option[Int]), FM.isNone[Int])
    )
  }

  @Test
  def testExistsMatcher(): Unit = {
    MatcherAssert.assertThat(Some(5), FM.exists[Int](_ > 2, "_ > 2"))
    AssertException(anyAssert) {
      MatcherAssert.assertThat(Some(1), FM.exists[Int](_ > 2, "_ > 2"))
    }
    AssertException(assertWithMessage("expected: some\\[int\\] matching predicate: _ > 2".r)) {
      MatcherAssert.assertThat(None: Option[Int], FM.exists[Int](_ > 2, "_ > 2"))
    }
  }

  @Test
  def testEqualsCollectionMatcher(): Unit = {
    MatcherAssert.assertThat(Vector(1, 2), FM.equalsCollection(Vector(1, 2)))
    AssertException(assertWithMessage("lengths differed".r))(
      MatcherAssert.assertThat(Vector(1), FM.equalsCollection(Vector(1, 2)))
    )
    AssertException(assertWithMessage("index 0.*expected=2.*actual=1".r))(
      MatcherAssert.assertThat(Vector(1), FM.equalsCollection(Vector(2)))
    )
  }
}
