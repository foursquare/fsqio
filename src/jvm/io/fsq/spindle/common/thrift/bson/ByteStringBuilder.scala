// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.common.thrift.bson

import java.io.InputStream
import java.lang.reflect.Constructor
import java.nio.charset.StandardCharsets

private[bson] object ByteStringBuilder {
  val MaxSize = 16 * 1024 * 1024
  val StringConstructor: Constructor[String] = {
    try {
      val constructor = {
        classOf[String].getDeclaredConstructors.find(_.toString == "java.lang.String(char[],boolean)").get
      }
      constructor.setAccessible(true)
      constructor.asInstanceOf[Constructor[String]]
    } catch {
      case e: Exception =>
        System.err.println("Error reflecting for String(chars, boolean) private constructor. Will use public constructor.")
        e.printStackTrace
        null
    }
  }

  val SingleByteStrings: Array[String] = (32 to 127).map(b => new String(Array(b.toChar))).toArray

  def byteToString(b: Byte): String = {
    SingleByteStrings(b - 32)
  }
}

/**
 * Growable buffer for building strings. Not thread safe
 * contains a bunch of performance hacks
 * reset() must be called before each re-use
 */
class ByteStringBuilder(initialSize: Int) {
  private var bytes = new Array[Byte](initialSize)
  private var length = 0
  private var isAscii = true
  
  private def ensureGrowth(size: Int) {
    val newRequestedSize = length + size
    if (newRequestedSize > bytes.length) {
      if (newRequestedSize > ByteStringBuilder.MaxSize) {
        throw new RuntimeException(s"Attempting to grow string builder past maximum size $newRequestedSize")
      }
      val newLength = math.min(newRequestedSize * 1.25, ByteStringBuilder.MaxSize).toInt
      val newBytes = new Array[Byte](newLength)
      System.arraycopy(bytes, 0, newBytes, 0, length)
      bytes = newBytes
    }
  }

  private def checkAscii(b: Byte) {
    isAscii = isAscii && b >= 32 && b <= 127
  }

  def append(b: Byte) {
    ensureGrowth(1)
    checkAscii(b)
    bytes(length) = b
    length += 1
  }

  def reset() {
    length = 0
    isAscii = true
  }

  def build(): String = {
    // Let the performance hacks begin:
    // 1. don't construct new empty string
    // 2. use cached single-character ascii strings
    // 3. if all bytes are ascii printable chars, don't do charset conversion
    // 4. if available, use jvm no-copy private String constructor
    if (length == 0) {
      ""
    } else if (isAscii && length == 1) {
      ByteStringBuilder.byteToString(bytes(0))
    } else if (isAscii) {
      val chars = new Array[Char](length)
      var counter = 0
      while (counter < length) {
        chars(counter) = bytes(counter).toChar
        counter += 1
      }
      if (ByteStringBuilder.StringConstructor != null) {
        ByteStringBuilder.StringConstructor.newInstance(chars, true: java.lang.Boolean)
      } else {
        new String(chars)
      }
    } else {
      new String(bytes, 0, length, StandardCharsets.UTF_8)
    }
  }

  /**
   * copy bytes from InputStream into this builder
   */
  def read(is: InputStream, readLength: Int) {
    ensureGrowth(readLength)
    var counter = 0
    while (counter < readLength) {
      val b: Byte = is.read().toByte
      checkAscii(b)
      bytes(length + counter) = b
      counter += 1
    }
    length += readLength
  }
}