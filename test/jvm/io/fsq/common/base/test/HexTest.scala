// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.common.base.test

import io.fsq.common.base.Hex
import java.nio.ByteBuffer
import org.junit.{Assert, Test}

class HexTest {

  private def doTestHexStringConversion(expected: String, in: Array[Byte]) {
    val hexStr = Hex.toHexString(in)
    Assert.assertEquals(expected, hexStr)

    val roundtrippedBytes = Hex.fromHexString(hexStr)
    Assert.assertArrayEquals(in, roundtrippedBytes)
  }

  @Test
  def testHexToStringLower(): Unit = {
    Assert.assertEquals("68692c20686f772061726520796f753f", Hex.toHexString("hi, how are you?".getBytes, true))
  }

  @Test
  def testHexStringConversion(): Unit = {
    doTestHexStringConversion("", Array())
    doTestHexStringConversion("00", Array(0.toByte))
    doTestHexStringConversion("01", Array(1.toByte))
    doTestHexStringConversion("7F", Array(127.toByte))
    doTestHexStringConversion("80", Array(128.toByte))
    doTestHexStringConversion("FF", Array(255.toByte))
    doTestHexStringConversion("00FF", Array(0.toByte, 255.toByte))
    doTestHexStringConversion("0001020304", Array(0.toByte, 1.toByte, 2.toByte, 3.toByte, 4.toByte))
  }

  @Test
  def testSliceHexStringConversion(): Unit = {
    val in = Array(0.toByte, 1.toByte, 2.toByte, 3.toByte, 4.toByte)
    val sliceHexStr = Hex.toHexString(in, 1, 2)
    Assert.assertEquals("0102", sliceHexStr)
  }

  @Test
  def testByteBufferToHexString(): Unit = {
    val in = "Hello There"
    val bb = ByteBuffer.wrap(in.getBytes)
    val initialSize = bb.remaining
    Assert.assertEquals(in, new String(Hex.fromHexString(Hex.toHexString(bb, true))))
    Assert.assertEquals("toHexString shouldn't modify the byte buffer", initialSize, bb.remaining)
  }
}
