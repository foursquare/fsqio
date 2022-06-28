// Copyright 2022 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.testlib

import java.lang.AssertionError
import scala.reflect.ClassTag

object FSAssert {
  // A helper method to verify that a block of code does throw a particular exception. Returns
  // the exception thrown.
  // Sample usage
  // ```
  // val exception = FSAssert.assertThrows[RuntimeException](code.that.throwsRuntimeException())
  // Assert.assertEquals("Expected message", exception.getMessage)
  // ```
  def assertThrows[E <: Throwable](f: => Any)(implicit errorType: ClassTag[E]): E = {
    try {
      f

      // We expect `f` to throw. If it did not, then this assertion has failed.
      throw new AssertionError(
        s"Expected exception of type ${errorType.runtimeClass.getName} but no exception was thrown."
      )
    } catch {
      case e: Exception => {
        if (errorType.runtimeClass.isAssignableFrom(e.getClass)) {
          e.asInstanceOf[E]
        } else {
          throw new AssertionError(
            s"Expected exception of type ${errorType.runtimeClass.getName} but found ${e.getClass.getName}"
          )
        }
      }
    }
  }
}
