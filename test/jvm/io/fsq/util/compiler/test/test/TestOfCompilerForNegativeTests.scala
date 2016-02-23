// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.util.compiler.test.test

import io.fsq.util.compiler.test.CompilerForNegativeTests
import org.junit.{Assert => A, Test}

case class TestCaseClass(v: Int)

class TestOfCompilerForNegativeTests {
  @Test
  def testCompiler(): Unit = {
    val compiler = new CompilerForNegativeTests(Seq("io.fsq.util.compiler.test.test.TestCaseClass"))

    val result1 = compiler.typeCheck("1 + 1")
    A.assertTrue(result1.isEmpty)

    val result2 = compiler.typeCheck("class def foo")
    A.assertTrue(result2.exists(_.contains("identifier expected")))

    val result3 = compiler.typeCheck("TestCaseClass(1)")
    A.assertTrue(result3.isEmpty)
  }
}
