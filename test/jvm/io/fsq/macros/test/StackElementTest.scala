// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.macros.test

import io.fsq.macros.StackElement._
import org.junit.Assert.assertEquals
import org.junit.Test

object StackElementTest {
  val ThisFile = "StackElementTest.scala"
  val ThisClass = "io.fsq.macros.test.StackElementTest"
}

class StackElementTest {
  @Test
  def testLine {
    val firstLine = STACKELEMENT
    assertEquals(firstLine.getLineNumber + 1, STACKELEMENT.getLineNumber)
  }

  @Test
  def testFile {
    assertEquals(StackElementTest.ThisFile, STACKELEMENT.getFileName)
  }

  @Test
  def testMethod {
    assertEquals("testMethod", STACKELEMENT.getMethodName)
  }

  @Test
  def testClass {
    assertEquals(StackElementTest.ThisClass, STACKELEMENT.getClassName)
  }

  @Test
  def testImplicit {
    val firstRef = ImplicitTestHelper.testStackElementImplicit()
    val secondRef = ImplicitTestHelper.testStackElementImplicit()
    assertEquals(firstRef.getLineNumber + 1, secondRef.getLineNumber)
    assertEquals(StackElementTest.ThisFile, firstRef.getFileName)
    assertEquals(StackElementTest.ThisClass, firstRef.getClassName)
    assertEquals("testImplicit", firstRef.getMethodName)
    assertEquals(StackElementTest.ThisFile, secondRef.getFileName)
    assertEquals(StackElementTest.ThisClass, secondRef.getClassName)
    assertEquals("testImplicit", secondRef.getMethodName)
  }
}

