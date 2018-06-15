// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.common.testing

import io.fsq.common.scala.Identity._
import org.junit.{Assert => A}

/**
  * This helper for JUnit tests allows you to verify that a block of code throws
  * an exception. Pass in a PartialFunction from Exceptions to Booleans that should
  * match and return true if the exception was expected. If this function does not match,
  * or the code does not throw an exception, an assertion will be raised.
  *
  * Example:
  *   AssertException("Should throw", { case e: Exception => e.getMessage.contains("thing") })({
  *     throw new Exception("thing!")
  *   })
  */
object AssertException {

  /**
    * @param msg Message to be shown on failure.
    * @param exceptionMatcher Should return true if the exception was expected.
    * @param body The thunk to execute that should throw an exception.
    */
  def apply(
    msg: String,
    exceptionMatcher: PartialFunction[Throwable, Boolean]
  )(body: => Unit): Unit = {
    def doFail() = if (msg !=? "") {
      A.fail(msg)
    } else {
      A.fail("Expected exception, but code did not throw or exception was not of expected type")
    }

    try {
      body
      doFail()
    } catch {
      case t: Throwable => {
        if (!exceptionMatcher.isDefinedAt(t) || !exceptionMatcher(t)) {
          doFail()
        }
      }
    }
  }

  /**
    * @param exceptionMatcher Should return true if the exception was expected.
    * @param body The thunk to execute that should throw an exception.
    */
  def apply(
    exceptionMatcher: PartialFunction[Throwable, Boolean]
  )(body: => Unit): Unit = {
    apply(
      "",
      exceptionMatcher
    )(body)
  }

  /**
    * You might find this convenient if you want to verify that an exception matches
    * a given class and that the message matches a regex. Emits descriptive error
    * messages.
    *
    * @param klass Exception must match this class (e.g. classOf[Foo]).
    * @param msgRegex Regular expression to match against the exception message.
    * @param body The thunk to execute that should throw an exception.
    */
  def matchesClassAndRegex[T <: Throwable](
    klass: Class[T],
    msgRegex: String
  )(body: => Unit): Unit = {
    try {
      body
      A.fail(s"Should have thrown a ${klass.getSimpleName}, but succeeded")
    } catch {
      case e: Exception if e.getClass == klass => {
        if (!e.getMessage.matches(msgRegex)) {
          A.fail(s"""Message failed to match regex "${msgRegex}": "${e.getMessage}"""")
        }
      }
      case e: Exception => throw e
    }
  }
}
