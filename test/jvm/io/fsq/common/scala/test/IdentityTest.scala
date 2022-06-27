// Copyright 2021 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.common.scala.test

import io.fsq.common.scala.Identity._
import org.junit.{Assert => A, Test}

class IdentityTest {
  @Test
  def testEquals(): Unit = {
    A.assertTrue(1 =? 1)
    A.assertFalse(1 =? 2)
    A.assertTrue("a" =? new String(Array[Char]('a')))
    A.assertFalse("a" =? new String(Array[Char]('b')))
  }

  @Test
  def testNotEquals(): Unit = {
    A.assertFalse(1 !=? 1)
    A.assertTrue(1 !=? 2)
    A.assertFalse("a" !=? new String(Array[Char]('a')))
    A.assertTrue("a" !=? new String(Array[Char]('b')))
  }

  @Test
  def testOptionally(): Unit = {
    A.assertEquals(Some(1), true.optionally(1))
    A.assertEquals(None, false.optionally(1))
  }

  def testFlatOptionally(): Unit = {
    A.assertEquals(Some(1), true.flatOptionally(Some(1)))
    A.assertEquals(None, false.flatOptionally(Some(1)))
    A.assertEquals(None, true.flatOptionally(None))
  }

  def testIfOption(): Unit = {
    A.assertEquals(Some(2), 1.ifOption(_ =? 1)(_ + 1))
    A.assertEquals(None, 1.ifOption(_ =? 2)(_ + 1))
  }

  def testApplyIf(): Unit = {
    A.assertEquals("A", "a".applyIf(pred = true, _.toUpperCase))
    A.assertEquals("a", "a".applyIf(pred = false, _.toUpperCase))
  }

  def testApplyIfElse(): Unit = {
    A.assertEquals(2, 1.applyIfElse(pred = true, _ + 1, _ + 2))
    A.assertEquals(3, 1.applyIfElse(pred = false, _ + 1, _ + 2))
    A.assertEquals("12", 1.applyIfElse(pred = true, _ + "1", _ + "2"))
    A.assertEquals("13", 1.applyIfElse(pred = false, _ + "1", _ + "2"))
  }

  def testApplyIfFn(): Unit = {
    A.assertEquals(1, 1.applyIfFn(_ =? 1, _ + 1))
    A.assertEquals(1, 1.applyIfFn(_ =? 2, _ + 1))
  }

  def testApplyOpt(): Unit = {
    A.assertEquals(3, 1.applyOpt(Some(2))((a, b) => a + b))
    A.assertEquals(1, 1.applyOpt(None: Option[Int])((a, b) => a + b))
    A.assertEquals(2, 1.applyOpt(Some("a"))((a, b) => a + b.length))
  }

  def testWithMinOf(): Unit = {
    A.assertEquals(10, 10.withMinOf(5))
    A.assertEquals(15, 10.withMinOf(15))
  }

  def testWithMaxOf(): Unit = {
    A.assertEquals(5, 10.withMaxOf(5))
    A.assertEquals(10, 10.withMaxOf(15))
  }

  def testBetween(): Unit = {
    A.assertTrue(5.between(2, 6))
    A.assertTrue(5.between(2, 5))
    A.assertTrue(5.between(5, 6))
    A.assertFalse(5.between(1, 2))
    A.assertFalse(5.between(7, 8))
  }
}
