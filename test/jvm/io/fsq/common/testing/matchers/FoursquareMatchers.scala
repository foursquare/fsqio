// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.common.testing.matchers

import org.hamcrest.{BaseMatcher, Description, Matcher}
import scala.collection.TraversableLike

object FoursquareMatchers {
  class IsNone[T] extends BaseMatcher[Option[T]] {
    override def describeTo(d: Description) = d.appendText("Is not None")

    override def matches(other: Object) = other == None
  }

  def isNone[T]: Matcher[Option[T]] = new IsNone[T]()

  object IsNonEmpty extends BaseMatcher {
    override def describeTo(d: Description) = d.appendText("Not a non-empty collection")

    override def matches(other: Object) = {
      other match {
        case other: TraversableLike[_, _] => other.nonEmpty
        case _ => false
      }
    }
  }

  // TODO(patrick): If we ever update to hamcrest 1.3, we can use the existing matcher that does
  // this, but this is handy because it accepts scala collections instead of java collections
  class HasItem(matchers: Seq[Matcher[_]]) extends BaseMatcher {
    override def describeTo(d: Description) = {
      d.appendText("Didn't match any of:")
      matchers.foreach(m => m.describeTo(d))
    }

    override def matches(other: Object) = {
      other match {
        case other: TraversableLike[_, _] => other.exists(t => matchers.exists(m => m.matches(t)))
        case _ => false
      }
    }
  }

  // TODO(patrick): If we ever update to hamcrest 1.3, we can use the existing matcher that does
  // this, but this is handy because it accepts scala collections instead of java collections
  class ContainsInAnyOrder(matchers: Seq[Matcher[_]]) extends BaseMatcher {
    override def describeTo(d: Description) = {
      d.appendText("Didn't match in any order:")
      matchers.foreach(m => m.describeTo(d))
    }

    override def matches(other: Object) = {
      other match {
        case other: TraversableLike[_, _] => other.forall(t => matchers.exists(m => m.matches(t)))
        case _ => false
      }
    }
  }
}

