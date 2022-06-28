// Copyright 2022 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.testlib.test

import io.fsq.testlib.FSAssert
import java.lang.AssertionError
import org.junit.{Assert, Test}

class FSAssertTest {
  @Test
  def testCatchesAndReturnsMatchedException(): Unit = {
    val msg = "this is the message"
    val e = FSAssert.assertThrows[RuntimeException](throw new RuntimeException(msg))
    Assert.assertEquals(msg, e.getMessage)
  }

  @Test
  def testCatchesAndReturnsSubclassException(): Unit = {
    val msg = "this is the message"
    val e = FSAssert.assertThrows[Exception](throw new RuntimeException(msg))
    Assert.assertEquals(msg, e.getMessage)
  }

  @Test
  def testDoesNotCatchNonMatchingException(): Unit = {
    var thrownException: Throwable = null
    val msg = "this is the message"
    try {
      FSAssert.assertThrows[RuntimeException](throw new Exception(msg))
    } catch {
      case e: Throwable => {
        thrownException = e
      }
    }

    Assert.assertNotNull(
      "assertThrows should have thrown an Exception that did not match the expected Exception " +
        "type, but it did not throw anything.",
      thrownException
    )
    Assert.assertEquals(msg, msg)
  }

  @Test
  def testThrowsIfNoException(): Unit = {
    var thrownException: Throwable = null
    try {
      // FSAssert.assertThrows[RuntimeException]("this is not an Exception")
      FSAssert.assertThrows[RuntimeException]("this is not an Exception")
    } catch {
      case e: Throwable => {
        thrownException = e
      }
    }

    Assert.assertNotNull("assertThrows should have thrown AssertionError, but didn't throw anything", thrownException)
    Assert.assertTrue(
      s"Expected `java.lang.AssertionError` but got `${thrownException.getClass.getName}`",
      thrownException.getClass.isAssignableFrom(classOf[AssertionError])
    )
    val msg = thrownException.getMessage
    Assert.assertTrue(s"Unexpected assertion failure message: ${msg}", msg.startsWith("Expected exception of type"))
  }
}
