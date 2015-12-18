// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.common.thrift.bson

import java.io.InputStream

/**
 * some helper functions for reading and writing little endian numbers from streams
 */
object StreamHelper {
  val MaxDocSize = 16 * 1024 * 1024

  def readInt(is: InputStream): Int = {
    val ch1 = 0xFF & is.read()
    val ch2 = 0xFF & is.read()
    val ch3 = 0xFF & is.read()
    val ch4 = 0xFF & is.read()

    // little endian
    ((ch4 << 24) + (ch3 << 16) + (ch2 << 8) + (ch1 << 0))
  }

  def writeInt(bytes: Array[Byte], offset: Int,  i: Int) {
    bytes(offset) = ((i << 24) >>> 24).toByte
    bytes(offset + 1) = ((i << 16) >>> 24).toByte
    bytes(offset + 2) = ((i << 8) >>> 24).toByte
    bytes(offset + 3) = (i >>> 24).toByte
  }

  def readLong(is: InputStream): Long = {
    val ch1 = 0xFFL & is.read()
    val ch2 = 0xFFL & is.read()
    val ch3 = 0xFFL & is.read()
    val ch4 = 0xFFL & is.read()
    val ch5 = 0xFFL & is.read()
    val ch6 = 0xFFL & is.read()
    val ch7 = 0xFFL & is.read()
    val ch8 = 0xFFL & is.read()

    // little endian
    (
      (ch8 << 56) + (ch7 << 48) + (ch6 << 40) + (ch5 << 32) +
      (ch4 << 24) + (ch3 << 16) + (ch2 << 8) + (ch1 << 0)
    )
  }
}