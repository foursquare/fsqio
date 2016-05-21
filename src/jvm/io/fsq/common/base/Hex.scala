// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.common.base

import java.nio.ByteBuffer

object Hex {
  val Hexits = Array[Char]('0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F')
  val HexitsLower = Array[Char]('0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f')

  private def writeHexits(buf: Array[Char], offset: Int, byte: Byte, lowerCase: Boolean): Unit = {
    val hexits = if (lowerCase) HexitsLower else Hexits
    buf(offset) = hexits((byte & 0xF0) >> 4)
    buf(offset + 1) = hexits(byte & 0x0F)
  }

  def toHexString(buf: Traversable[Byte]): String = toHexString(buf, false)
  def toHexString(buf: Traversable[Byte], lowerCase: Boolean): String = {
    val charBuf = new Array[Char](buf.size * 2)

    var i = 0
    buf.foreach { b =>
      writeHexits(charBuf, i * 2, b, lowerCase)
      i += 1
    }
    new String(charBuf)
  }

  def toHexString(buf: Array[Byte]): String = toHexString(buf, false)
  def toHexString(buf: Array[Byte], lowerCase: Boolean): String = toHexString(buf, 0, buf.length, lowerCase)

  def toHexString(buf: Array[Byte], from: Int, len: Int): String = toHexString(buf, from, len, false)
  def toHexString(buf: Array[Byte], from: Int, len: Int, lowerCase: Boolean): String = {
    if (from + len > buf.length) {
      throw new IllegalArgumentException("from + len must be less than or equal to buf.length")
    }
    val charBuf = new Array[Char](len * 2)

    var i = 0
    while (i < len) {
      writeHexits(charBuf, i * 2, buf(from + i), lowerCase)
      i += 1
    }

    new String(charBuf)
  }

  def toHexString(buf: ByteBuffer, lowerCase: Boolean): String = {
    val bufCopy = buf.asReadOnlyBuffer()
    val charBuf = new Array[Char](bufCopy.remaining() * 2)

    var i = 0
    while(bufCopy.remaining() > 0) {
      writeHexits(charBuf, i * 2, bufCopy.get, lowerCase)
      i += 1
    }

    new String(charBuf)
  }

  def fromHexString(str: String): Array[Byte] = {
    if (str.length % 2 != 0) {
      throw new Exception("Hex string must have even number of characters: " + str)
    }
    val ret = new Array[Byte](str.length / 2)
    for (i <- 0 until ret.length) {
      ret(i) = (16 * Character.digit(str.charAt(2 * i), 16) + Character.digit(str.charAt(2 * i + 1), 16)).toByte
    }
    ret
  }

  def hexToLong(hexStr: String): Long = {
    if (hexStr.size < 16) {
      // If less than 16 characters, then we can go ahead and use parseLong.
      java.lang.Long.parseLong(hexStr, 16)
    } else if (hexStr.size > 16) {
        throw new Exception("Number too large to fit in a long.")
    } else {
      // If the most-significant (sign) bit is set, we need to convert to signed version.
      hexStr.head match {
        case 'F' => Long.MinValue + java.lang.Long.parseLong("7" + hexStr.tail, 16)
        case 'E' => Long.MinValue + java.lang.Long.parseLong("6" + hexStr.tail, 16)
        case 'D' => Long.MinValue + java.lang.Long.parseLong("5" + hexStr.tail, 16)
        case 'C' => Long.MinValue + java.lang.Long.parseLong("4" + hexStr.tail, 16)
        case 'B' => Long.MinValue + java.lang.Long.parseLong("3" + hexStr.tail, 16)
        case 'A' => Long.MinValue + java.lang.Long.parseLong("2" + hexStr.tail, 16)
        case '9' => Long.MinValue + java.lang.Long.parseLong("1" + hexStr.tail, 16)
        case '8' => Long.MinValue + java.lang.Long.parseLong(hexStr.tail, 16)
        case _ => java.lang.Long.parseLong(hexStr, 16)
      }
    }
  }
}
