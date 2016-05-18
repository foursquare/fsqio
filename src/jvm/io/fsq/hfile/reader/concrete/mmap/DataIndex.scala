// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.hfile.reader.concrete.mmap

import io.fsq.common.logging.Logger
import io.fsq.common.scala.Identity._
import java.nio.ByteBuffer
import java.util.Arrays


case class DataBlockInfo(indexByteOffset: Int, keyOffset: Short, keyLength: Short, hfileByteOffset: Long)

class DataIndex(
    blockReader: BlockReader,
    buffer: ByteBuffer,
    bufferSize: Int,
    val blockCount: Int,
    compressionCodec: Int,
    lastOffset: Long,
    trackCacheStats: Boolean)
    extends Logger {
  val indexOffsets = new Array[DataBlockInfo](blockCount)

  val MidKeyMetaDataSize = Bytes.SizeOfLong + (2 * Bytes.SizeOfInt)

  // check for magic which signals the start of the DataIndex
  val magic = new Array[Byte](8)

  buffer.get(magic)
  if (Arrays.equals(magic, "IDXBLK)+".getBytes()) !=? true)
    throw new Exception("bad data index magic")


  val accessLogger:Option[CacheSim[Int]] = if (trackCacheStats) {
    Some(CacheSim.makeSim[Int](blockCount))
  } else {
    None
  }

  {
    var dataBlockCount = 0
    var currentBufferStartOffset: Long = 0
    var currentBuffer: ByteBuffer = null
    while (buffer.position < bufferSize) {
      val indexOffset = buffer.position()

      val offset = buffer.getLong()
      val uncompressedSize = buffer.getInt()
      val (sizeLength, keyLength) = Bytes.readVarlenByteSeqOffsets(buffer)
      indexOffsets(dataBlockCount) = DataBlockInfo(indexOffset, (sizeLength + 12).toShort, keyLength, offset)
      val nextOffset = if (dataBlockCount == blockCount-1) { lastOffset } else { buffer.slice.getLong }
      dataBlockCount += 1
    }
    assert(buffer.position =? bufferSize, "the parsing of the DataIndex is incorrect")
  }

  private def firstKeyOfBlock(index: Int): ByteBuffer = {
    val bufferSlice = buffer.duplicate
    bufferSlice.position(indexOffsets(index).indexByteOffset)
    val offset = bufferSlice.getLong()
    val uncompressedSize = bufferSlice.getInt()
    Bytes.readVarlenByteSeq(bufferSlice)
  }

  val firstKey: ByteBuffer = firstKeyOfBlock(0)

  def dataBlock(index: Int): DataBlock = {
    val offsets = indexOffsets(index)
    val bufferSlice = buffer.duplicate
    bufferSlice.position(offsets.indexByteOffset)
    val offset = bufferSlice.getLong()
    val uncompressedSize = bufferSlice.getInt()
    bufferSlice.position(offsets.indexByteOffset + offsets.keyOffset + offsets.keyLength)
    val nextOffset = if (index == blockCount-1) { lastOffset } else { bufferSlice.getLong }

    new DataBlock(
      compressionCodec,
      blockReader(offset, nextOffset-offset),
      uncompressedSize, index, accessLogger)
  }

  def compareFirstKey(key: ByteBuffer, index: Int): Int = {
    val offsets = indexOffsets(index)
    Bytes.compare(key, buffer, offsets.indexByteOffset + offsets.keyOffset, offsets.keyLength)
  }

  def compareFirstKey(splitKey: Seq[ByteBuffer], index: Int): Int = {
    val offsets = indexOffsets(index)
    Bytes.compare(splitKey, buffer, offsets.indexByteOffset + offsets.keyOffset, offsets.keyLength)
  }

  val size = heapSize(blockCount)

  def heapSize(blockCount: Int): Long = {
    var heapSize = 0L

    heapSize += ClassSize.align(
      (6 * ClassSize.REFERENCE) + (3 * Bytes.SizeOfInt) + ClassSize.OBJECT
    )

    heapSize += MidKeyMetaDataSize

    heapSize += ClassSize.align(
      ClassSize.ARRAY + (blockCount * ClassSize.REFERENCE)
    )

    for (i <- 0 until blockCount) {
      heapSize += ClassSize.align(
        ClassSize.ARRAY + firstKeyOfBlock(i).remaining
      )
    }

    heapSize += ClassSize.align(
      ClassSize.ARRAY + (blockCount * Bytes.SizeOfLong)
    )

    heapSize += ClassSize.align(
      ClassSize.ARRAY + (blockCount * Bytes.SizeOfInt)
    )

    ClassSize.align(heapSize)
  }


}
