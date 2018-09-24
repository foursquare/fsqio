// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.common.testing.matchers

import io.fsq.common.scala.Identity._
import org.hamcrest.{BaseMatcher, Description, Matcher}
import scala.math.max
import scala.reflect.runtime.universe.TypeTag

object FoursquareMatchers {
  private[matchers] class IsNone[T] extends BaseMatcher[Option[T]] {
    override def describeTo(d: Description) = d.appendText("Is not None")

    override def matches(other: Object) = other == None
  }

  def isNone[T]: Matcher[Option[T]] = new IsNone[T]()

  private class Exists[T: TypeTag](
    predicate: T => Boolean,
    predicateAsString: String
  ) extends BaseMatcher[Option[T]] {

    override def describeTo(d: Description): Unit = {
      d.appendText(s"Some[${implicitly[TypeTag[T]].tpe}] matching predicate: $predicateAsString")
    }

    override def matches(other: Object): Boolean = other match {
      case optionT: Option[T] => optionT.exists(predicate)
      case _ => false
    }
  }

  def exists[T: TypeTag](
    predicate: T => Boolean,
    predicateAsString: String = "omitted"
  ): Matcher[Option[T]] = {
    new Exists(predicate, predicateAsString)
  }

  private[matchers] class EqualsCollection[A](expected: Iterable[A]) extends BaseMatcher[Iterable[A]] {
    private val MaxElementsToDisplay = 10

    override def describeTo(d: Description) = {
      d.appendText(s"equal to the collection: ${expected}")
    }

    override def describeMismatch(item: Object, d: Description): Unit = {
      val actual = item.asInstanceOf[Iterable[A]]
      val clauses = Vector.newBuilder[String]
      if (expected.size !=? actual.size) {
        clauses += s"collection lengths differed: expected.size=${expected.size}, actual.size=${actual.size}"
      }
      var numTotalDifferent = 0
      expected
        .zip(actual)
        .zipWithIndex
        .foreach({
          case ((e, a), i) => {
            if (e !=? a) {
              numTotalDifferent += 1
              if (numTotalDifferent <= MaxElementsToDisplay) {
                clauses += s"element at index ${i} differed: expected=${e}, actual=${a}"
              }
            }
          }
        })
      val extraElementsThatWereDifferent = max(0, numTotalDifferent - MaxElementsToDisplay)
      if (extraElementsThatWereDifferent > 0) {
        clauses += s"${extraElementsThatWereDifferent} additional elements differed"
      }
      val descriptionString = clauses.result().mkString("; ")
      d.appendText(descriptionString)
    }

    override def matches(other: Object): Boolean = other == expected
  }

  /**
    * Hamcrest matcher that asserts a given Scala collection is equal to the provided collection,
    * and provides helpful error messages.
    *
    * Examples:
    *   assertThat(List(1, 2), equalsCollection(List(1, 2))) // Pass
    *   assertThat(List(2, 2), equalsCollection(List(1, 2))) // element at index 0 differed: expected=1, actual=2
    *   assertThat(List(1), equalsCollection(List(1, 2))) // collection lengths differed: expected.size=2, actual.size=1
    */
  def equalsCollection[A](expected: Iterable[A]): Matcher[Iterable[A]] = {
    new EqualsCollection(expected)
  }
}
