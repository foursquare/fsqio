// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.hfile.writer.concrete

import java.io.DataOutputStream
import scala.math.Ordering._

trait Bytes {
  def writeVariableLong(i: Long, outputStream: DataOutputStream): Unit = {
    if (i >= -112 && i <= 127) {
      outputStream.writeByte(i.toByte)
    } else {
      var len = -112
      var input = i
      if (input < 0) {
        input ^= -1L
        len = -120
      }
      var tmp = input
      while (tmp != 0) {
        tmp = tmp >> 8
        len -= 1
      }
      outputStream.writeByte(len.toByte)
      len = if (len < -120) {
        -1 * (len + 120)
      } else {
        -1 * (len + 112)
      }

      for {
        idx <- len until 0 by -1
      } {
        val shiftbits = (idx - 1) * 8
        val mask = 0xFFL << shiftbits
        outputStream.writeByte(((i & mask) >> shiftbits).toByte)
      }
    }
  }

  def writeByteArray(by: Array[Byte], outputStream: DataOutputStream): Unit = {
    writeVariableLong(by.length, outputStream)
    outputStream.write(by, 0, by.length)
  }

  def longToBytes(v: Long): Array[Byte] = {
    val output = new Array[Byte](8)
    var buf = v
    for {
      idx <- 7 until 0 by -1
    } {
      output(idx) = buf.toByte
      buf >>>= 8
    }
    output(0) = buf.toByte
    output
  }

  def intToBytes(v: Int): Array[Byte] = {
    val output = new Array[Byte](4)
    var buf = v
    for {
      idx <- 3 until 0 by -1
    } {
      output(idx) = buf.toByte
      buf >>>= 8
    }
    output(0) = buf.toByte
    output
  }
}
