// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.hfile.reader.concrete.mmap

import io.fsq.common.base.Hex
import io.fsq.common.logging.Logger
import io.fsq.common.scala.Identity._
import java.nio.ByteBuffer
import java.util.Comparator
import org.apache.hadoop.io.WritableComparator

object Bytes extends Logger {
  val SizeOfInt = 4
  val SizeOfLong = 8

  def getByteArray(bb: ByteBuffer): Array[Byte] = {
      val bytes = new Array[Byte](bb.remaining)
      bb.slice.get(bytes)
      bytes
  }


  def copyDirectBufferToHeap(bb: ByteBuffer): ByteBuffer = {
    if (bb.isDirect) {
      ByteBuffer.allocate(bb.remaining).put(bb).flip.asInstanceOf[ByteBuffer]
    } else {
      bb
    }
  }

  def byteBufferToString(bb: ByteBuffer): String = Hex.toHexString(bb, lowerCase=false)

  def byteBufferToDecimalString(inputBb: ByteBuffer): String = {
    val bb = inputBb.slice
    val sb = new StringBuilder
    (0 until bb.limit) foreach {i =>
      sb.append(bb.get(i))
      if (i < bb.limit-1) { sb.append(", ") }
    }
    sb.toString
  }

  def readVarlenByteSeqOffsets(mbb: ByteBuffer): (Short, Short) = {
    val start = mbb.position
    val size = getVInt(mbb).toShort
    assert(size > 0)
    val byteSeqOffset = (mbb.position - start).toShort
    mbb.position(mbb.position + size)
    (byteSeqOffset, size)
  }

  def readVarlenByteSeq(mbb: ByteBuffer): ByteBuffer = {
    val size = getVInt(mbb)
    assert(size > 0)
    val bytes = mbb.slice
    logger.trace(s"p=${bytes.position}  c=${bytes.capacity}  r=${bytes.remaining} size=$size")
    bytes.limit(bytes.position + size)
    mbb.position(mbb.position + size)
    bytes
  }

  // VINT ------------------------------------------------------------------------------------------
  // http://grepcode.com/file/repo1.maven.org/maven2/org.apache.hadoop/hadoop-common/2.5.0/org/apache/hadoop/io/WritableUtils.java#WritableUtils.readVInt%28java.io.DataInput%29
  def getVInt(mbb: ByteBuffer): Int = {
    val firstByte: Byte = mbb.get()
    logger.trace(s"firstByte = $firstByte")
    val vIntSize = decodeVIntSize(firstByte)
    logger.trace(s"vIntSize = $vIntSize")
    if (vIntSize == 1)
      firstByte
    else {
      var vInt = 0
      for (i <- 0 to (vIntSize - 2)) {
        val byte: Byte = mbb.get()
        logger.trace(s"i=$i, byte=$byte")
        vInt = vInt << 8
        vInt = vInt | (byte & 0xFF)
      }
      (if (isNegativeVInt(firstByte)) (vInt ^ -1) else vInt)
    }
  }

  def decodeVIntSize(byte: Byte): Int = {
    if (byte >= -112)
      1
    else if (byte < -120)
      -119 - byte
    else
      -111 - byte
  }

  def isNegativeVInt(byte: Byte): Boolean = {
    byte < -120 || (byte >= -112 && byte < 0);
  }


  @inline
  def compare(left: ByteBuffer, right: ByteBuffer): Int = {
    compare(left, right, right.position, right.remaining)
  }

  /**
   * splitKey is treated as one byte buffer formed by sequential concatenation of all the byte buffers.
   * This function is essentially providing same functionality as the above except it takes the input byte buffer
   * in multiple chunks.
   */
  @inline
  def compare(splitKey: Seq[ByteBuffer], right: ByteBuffer): Int = {
    compare(splitKey, right, right.position, right.remaining)
  }

  @inline
  def compare(left: ByteBuffer, right: ByteBuffer, rightPosition: Int, rightRemaining: Int): Int = {
    if (left.hasArray && right.hasArray) {
      HadoopByteBufferComparator.compare(left, right, rightPosition, rightRemaining)
    } else {
      ByteBufferComparatorNoSlice.compare(left, right, rightPosition, rightRemaining)
    }
  }

  @inline
  def compare(splitKey: Seq[ByteBuffer], right: ByteBuffer, rightPosition: Int, rightRemaining: Int): Int = {
    if (splitKey.forall(_.hasArray) && right.hasArray) {
      HadoopByteBufferComparator.compare(splitKey, right, rightPosition, rightRemaining)
    } else {
      ByteBufferComparatorNoSlice.compare(splitKey, right, rightPosition, rightRemaining)
    }
  }

  def isPrefix(prefix: ByteBuffer, key: ByteBuffer): Boolean = {
    key.remaining >= prefix.remaining &&
    WritableComparator.compareBytes(prefix.array, prefix.arrayOffset + prefix.position, prefix.remaining,
      key.array, key.arrayOffset + key.position, prefix.remaining) == 0
  }

  def isSuffix(suffix: ByteBuffer, key: ByteBuffer): Boolean = {
    key.remaining >= suffix.remaining &&
    WritableComparator.compareBytes(suffix.array, suffix.arrayOffset + suffix.position, suffix.remaining,
      key.array, key.arrayOffset + key.position + key.remaining - suffix.remaining, suffix.remaining) == 0
  }

  object ByteBufferComparatorWithGetLong extends Comparator[ByteBuffer] {
    @inline
    def compare(left: ByteBuffer, right: ByteBuffer): Int = {
      val leftSlice = left.slice()
      val rightSlice = right.slice()

      if (leftSlice.limit == 0 || rightSlice.limit == 0) {
        leftSlice.limit - rightSlice.limit
      } else {
        var index = 0
        while (leftSlice.remaining >=8 && rightSlice.remaining >= 8 && leftSlice.getLong == rightSlice.getLong)  {
          index += 8
        }

        val end = Math.min(leftSlice.limit, rightSlice.limit) - 1
        while ((index < end) && (leftSlice.get(index) == rightSlice.get(index))) {
          index += 1
        }
        if (index <= end && leftSlice.get(index) != rightSlice.get(index)) {
          (leftSlice.get(index) & 0xff) - (rightSlice.get(index) & 0xff)
        } else {
          leftSlice.limit - rightSlice.limit
        }
      }
    }
  }

  object ByteBufferComparatorNoSlice extends Comparator[ByteBuffer] {
    @inline
    def compare(left: ByteBuffer, right: ByteBuffer): Int =
      compare(left, right, right.position, right.remaining)


    @inline
    def compare(left: ByteBuffer, right: ByteBuffer, rightPosition: Int, rightRemaining: Int): Int = {

      var end = left.position() + Math.min(left.remaining(), rightRemaining) -1
      var lidx = left.position()
      var ridx = rightPosition
      var compare = 0

      while (lidx < end && left.get(lidx) == right.get(ridx)) {
        lidx+=1
        ridx+=1
      }

      if (lidx <= end && left.get(lidx) != right.get(ridx)) {
        (left.get(lidx) & 0xff) - (right.get(ridx) & 0xff)
      } else {
        left.remaining - rightRemaining
      }
    }

    @inline
    def compare(lefts: Seq[ByteBuffer], right: ByteBuffer, rightPosition: Int, rightRemaining: Int): Int = {
      var res = 0
      var position = rightPosition
      var remaining = rightRemaining
      for (left <- lefts if res == 0) {
        res = compare(left, right, position, Math.min(left.remaining, remaining))
        position += left.remaining
        remaining -= left.remaining
      }
      if (res != 0) res else -remaining
    }

  }

  object HadoopByteBufferComparator extends Comparator[ByteBuffer] {
    @inline
    def compare(left: ByteBuffer, right: ByteBuffer): Int =
      compare(left, right, right.position, right.remaining)

    @inline
    def compare(left: ByteBuffer, right: ByteBuffer, rightPosition: Int, rightRemaining: Int): Int = {
      WritableComparator.compareBytes(left.array(), left.arrayOffset + left.position, left.remaining,
        right.array(), right.arrayOffset + rightPosition, rightRemaining)
    }

    @inline
    def compare(lefts: Seq[ByteBuffer], right: ByteBuffer, rightPosition: Int, rightRemaining: Int): Int = {
      var res = 0
      var position = rightPosition
      var remaining = rightRemaining
      for (left <- lefts if res == 0) {
        res = compare(left, right, position, Math.min(left.remaining, remaining))
        position += left.remaining
        remaining -= left.remaining
      }
      if (res != 0) res else -remaining
    }

  }


  // Don't use this unless you run into heap size issues. Reflection is slow.
  def freeDirectByteBuffer(buffer: ByteBuffer): Unit = {
    if (buffer != null && buffer.isDirect) {
      val cleanerMethod = buffer.getClass().getMethod("cleaner")
      cleanerMethod.setAccessible(true)
      val cleaner = cleanerMethod.invoke(buffer)
      val cleanMethod = cleaner.getClass().getMethod("clean")
      cleanMethod.setAccessible(true)
      cleanMethod.invoke(cleaner)
    }
  }

}
