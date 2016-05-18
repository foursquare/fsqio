// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.hfile.reader.concrete.mmap

import com.twitter.ostrich.stats.Stats
import io.fsq.common.logging.Logger
import io.fsq.common.scala.Identity._
import java.nio.ByteBuffer
import java.util.Arrays
import org.xerial.snappy.Snappy

class DataBlock(
  compressionCodec: Int,
  buffer: ByteBuffer,
  uncompressedSize: Int,
  val index: Int,
  accessLogger: Option[CacheSim[Int]]
) extends Logger {

  // find closest offset s.t. key <= key[offset]
  // returns -1 if key < first key in this block
  def getClosestKeyOffset(inputKey: ByteBuffer, uncompressedBuffer: ByteBuffer, startOffset: Int): (Int, Boolean) = {
    uncompressedBuffer.position(startOffset)
    var currOffset = startOffset
    var prevOffset = startOffset
    var compareResult = 0

    do {
      prevOffset = currOffset
      currOffset = uncompressedBuffer.position
      compareResult = compareWithKeyAndMoveForward(inputKey, currOffset, uncompressedBuffer)
    } while( uncompressedBuffer.position < uncompressedBuffer.limit && compareResult > 0)

    // logger.debug(s"compareResult = $compareResult currOffset = $currOffset prevOffset = $prevOffset uncompressedBuffer.position = ${uncompressedBuffer.position} uncompressedBuffer.limit = ${uncompressedBuffer.limit}")
    // For an exact match, currOffset points to an entry with the same key as the inputKey.
    if (compareResult == 0) {
      (currOffset, true)
    } else if (compareResult > 0) {
    // If compareResult>0, we've reached the end of the block, and need to return a pointer to the last key in the block.
      (currOffset, false)
    } else { // (compareResult<0
      // Otherwise we need to rewind to the previous kv pair.
      (prevOffset, false)
    }
  }

  /**
   * Find closest offset where splitKey <= key[offset]
   * Returns -1 if splitKey < first key in this block.
   * splitKey is treated as one byteBuffer formed by sequential concatenation of all the byte buffers.
   * This function is essentially providing same functionality as the above except it takes the input byte buffer
   * in multiple chunks.
   */
  def getClosestKeyOffset(
    splitKey: Seq[ByteBuffer],
    uncompressedBuffer: ByteBuffer,
    startOffset: Int
  ): (Int, Boolean) = {
    uncompressedBuffer.position(startOffset)
    var currOffset = startOffset
    var prevOffset = startOffset
    var compareResult = 0

    do {
      prevOffset = currOffset
      currOffset = uncompressedBuffer.position
      compareResult = compareWithKeyAndMoveForward(splitKey, currOffset, uncompressedBuffer)
    } while( uncompressedBuffer.position < uncompressedBuffer.limit && compareResult > 0)

    // logger.debug(s"compareResult = $compareResult currOffset = $currOffset prevOffset = $prevOffset uncompressedBuffer.position = ${uncompressedBuffer.position} uncompressedBuffer.limit = ${uncompressedBuffer.limit}")
    // For an exact match, currOffset points to an entry with the same key as the splitKey.
    if (compareResult == 0) {
      (currOffset, true)
    } else if (compareResult > 0) {
    // If compareResult>0, we've reached the end of the block, and need to return a pointer to the last key in the block.
      (currOffset, false)
    } else { // (compareResult<0
      // Otherwise we need to rewind to the previous kv pair.
      (prevOffset, false)
    }
  }

  def compareWithKeyAndMoveForward(inputKey: ByteBuffer, currOffset: Int, uncompressedBuffer: ByteBuffer): Int = {
    // does the same as the following, but without a call to slice
    // DataBlock.compare(inputKey, getKey(currOffset, uncompressedBuffer))
    uncompressedBuffer.position(currOffset)

    val keyLen = uncompressedBuffer.getInt()
    val valLen = uncompressedBuffer.getInt()

    val keyStart = uncompressedBuffer.position
    uncompressedBuffer.position(uncompressedBuffer.position + keyLen + valLen)

    Bytes.compare(inputKey, uncompressedBuffer, keyStart, keyLen)
  }

  // same functionality as the above function but takes a key in form of continuous chunks instead of one byte buffer
  def compareWithKeyAndMoveForward(
    splitKey: Seq[ByteBuffer],
    currOffset: Int,
    uncompressedBuffer: ByteBuffer
  ): Int = {
    uncompressedBuffer.position(currOffset)

    val keyLen = uncompressedBuffer.getInt()
    val valLen = uncompressedBuffer.getInt()

    val keyStart = uncompressedBuffer.position
    uncompressedBuffer.position(uncompressedBuffer.position + keyLen + valLen)

    Bytes.compare(splitKey, uncompressedBuffer, keyStart, keyLen)
  }

  def nextOffset(offset: Int, uncompressedBuffer: ByteBuffer): Int = {
    uncompressedBuffer.position(offset)
    val keyLen = uncompressedBuffer.getInt()
    val valLen = uncompressedBuffer.getInt()
    uncompressedBuffer.position(uncompressedBuffer.position + keyLen + valLen)

    if (uncompressedBuffer.position == uncompressedBuffer.limit) { offset  } // end of DataBlock
    else { uncompressedBuffer.position }
  }

  // positions uncompressedBuffer at the start of the next key/value pair
  def getKey(atOffset: Int, uncompressedBuffer: ByteBuffer): ByteBuffer = {
    uncompressedBuffer.position(atOffset)
    getKey(uncompressedBuffer)
  }

  // positions uncompressedBuffer at the start of the next key/value pair
  private def getKey(uncompressedBuffer: ByteBuffer): ByteBuffer = {
    val keyLen = uncompressedBuffer.getInt()
    val valLen = uncompressedBuffer.getInt()

    // create a bytebuffer with just the key bytes
    val key = uncompressedBuffer.slice
    key.limit(key.position + keyLen)
    uncompressedBuffer.position(uncompressedBuffer.position + keyLen + valLen)

    key
  }

  // positions uncompressedBuffer at the start of the next key/value pair
  def getVal(offset: Int, uncompressedBuffer: ByteBuffer): ByteBuffer = {
    uncompressedBuffer.position(offset)
    getVal(uncompressedBuffer)
  }

  // positions uncompressedBuffer at the start of the next key/value pair
  private def getVal(uncompressedBuffer: ByteBuffer): ByteBuffer = {
    val keyLen = uncompressedBuffer.getInt()
    val valLen = uncompressedBuffer.getInt()

    // just the value bytes
    val value = uncompressedBuffer.slice
    value.position(value.position + keyLen)
    value.limit(value.position + valLen)
    uncompressedBuffer.position(uncompressedBuffer.position + keyLen + valLen)

    value
  }


  // BUFFER ----------------------------------------------------------------------------------------
  def resetBuffer(): ByteBuffer =  { synchronized {
    accessLogger.map(al => Stats.time("cachesim.enqueue") { al.access(index) })
    buffer.position(0)
    val uncompressedBuffer =
      if (compressionCodec =? 3) {
        val uncompressedBuffer = if (buffer.isDirect) {
          // The data needs to be uncompressed into a direct buffer because the source is a direct buffer
          ByteBuffer.allocateDirect(uncompressedSize)
        } else {
          ByteBuffer.allocate(uncompressedSize)
        }

        while (uncompressedBuffer.position < uncompressedSize) {
          buffer.limit(buffer.position() + 4)
          val originalBlockSize = buffer.getInt()
          var bytesReadInThisBlock = 0
          while (bytesReadInThisBlock < originalBlockSize) {
            buffer.limit(buffer.position() + 4)
            val compressedSize = buffer.getInt()
            buffer.limit(buffer.position() + compressedSize)
            val size = if (buffer.isDirect) {
              Snappy.uncompress(buffer, uncompressedBuffer)
            } else {
              Snappy.rawUncompress(
                buffer.array,
                buffer.arrayOffset + buffer.position,
                buffer.remaining,
                uncompressedBuffer.array,
                uncompressedBuffer.arrayOffset + uncompressedBuffer.position
              )
            }
            buffer.position(buffer.position + compressedSize)
            uncompressedBuffer.position(uncompressedBuffer.position + size)
            bytesReadInThisBlock += size
          }
        }
        uncompressedBuffer.flip
        assert(uncompressedBuffer.remaining == uncompressedSize,
          s"uncompressedBuffer.remaining of ${uncompressedBuffer.remaining} does not equal uncompressedSize of ${uncompressedSize} for block ${index}")
        uncompressedBuffer
      } else {
        val uncompressedBuffer = buffer.slice
        uncompressedBuffer.limit(uncompressedSize)
        uncompressedBuffer
      }
    // +8 in order to skip the magic header
    uncompressedBuffer.position(8)
    uncompressedBuffer
  }
  }

  // checks for magic which signals the start of the DataBlock
  def verifyMagic(): Unit = {
    val uncompressedBuffer = resetBuffer()
    uncompressedBuffer.position(0)
    val magic = new Array[Byte](8)
    uncompressedBuffer.get(magic)
    if (!(Arrays.equals(magic, "DATABLK*".getBytes())))
      throw new Exception(s"bad data block magic at index $index")
  }

}
