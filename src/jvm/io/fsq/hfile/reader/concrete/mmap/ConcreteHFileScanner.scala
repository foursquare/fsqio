// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.hfile.reader.concrete.mmap

import io.fsq.common.logging.Logger
import io.fsq.common.scala.Identity._
import io.fsq.hfile.reader.service.HFileScanner
import java.nio.ByteBuffer

class ConcreteHFileScanner(reader: ConcreteHFileReader) extends HFileScanner with Logger {
  var curDataBlock: DataBlock = _
  var prevDataBlock: DataBlock = _
  var curBuffer: ByteBuffer = _
  var curKVOffset: Int = _

  override def isSeeked(): Boolean = {
    curDataBlock != null
  }

  override def getKey(): ByteBuffer = {
    if (curDataBlock != null)
      curDataBlock.getKey(curKVOffset, curBuffer)
    else
      throw new Exception("you need to seekTo before calling getKey")
  }

  override def getValue(): java.nio.ByteBuffer = {
    if (curDataBlock != null)
      curDataBlock.getVal(curKVOffset, curBuffer)
    else
      throw new Exception("you need to seekTo before calling getValue")
  }

  override def next(): Boolean = {
    if (curDataBlock == null) {
      throw new Exception("Next called before seek on HFileScanner")
    }

    if (curKVOffset =? -1) {
      false
    } else {
      val nextOffset = curDataBlock.nextOffset(curKVOffset, curBuffer)
      val isEndOfDataBlock = (curKVOffset =? nextOffset)
      val isLastBlock = (curDataBlock.index =? reader.dataIndex.blockCount - 1)

      // NOTE(petko): Commenting out since this prints a ton of logs for 1:30 minutes on every rec server start.
      // logger.debug(s"isEndOfDataBlock = $isEndOfDataBlock curKVOffset = $curKVOffset nextOffset = $nextOffset")
      // logger.debug(s"isLastBlock = $isLastBlock curDataBlock.index = ${curDataBlock.index} reader.dataIndex.blockCount = ${reader.dataIndex.blockCount}")

      if (!isEndOfDataBlock) {
        curKVOffset = nextOffset
        true
      } else if (!isLastBlock) { // move to the next block
        // release curBuffer from cache
        curDataBlock = reader.dataIndex.dataBlock(curDataBlock.index + 1)
        setCurBuffer()
        curKVOffset = 8 // skip magic
        true
      } else // reached the end of the hfile : (isEndOfDataBlock && isLastBlock)
        false
    }
  }

  override def reseekTo(inputKey: ByteBuffer): Int = {
    seekToInner(inputKey, if (curDataBlock !=? null) curDataBlock.index else 0)
  }

  override def reseekTo(splitKey: Seq[ByteBuffer]): Int = {
    seekToInner(splitKey, if (curDataBlock !=? null) curDataBlock.index else 0)
  }

  override def rewind(): Boolean = {
    if (reader.dataIndex != null) {
      curDataBlock = reader.dataIndex.dataBlock(0)
      setCurBuffer()
      curKVOffset = 8 // +8 to skip magic
      true
    } else {
      false
    }
  }

  override def seekTo(inputKey: ByteBuffer): Int = {
    if (reader.dataIndex != null) {
      rewind()
      seekToInner(inputKey, 0)
    } else {
      -1
    }
  }

  override def seekTo(splitKey: Seq[ByteBuffer]): Int = {
    if (reader.dataIndex != null) {
      rewind()
      seekToInner(splitKey, 0)
    } else {
      -1
    }
  }

  val SeekToInnerAll = reader.hfilename + ".seekToInner.All"
  val SeekToInnerGetDataBlock = reader.hfilename + ".seekToInner.GetDataBlock"
  val SeekToInnerSetCurBuffer = reader.hfilename + ".seekToInner.SetCurBuffer"
  val SeekToInnerGetClosestOffset = reader.hfilename + ".seekToInner.GetClosestKeyOffset"

  private def seekToInner(inputKey: ByteBuffer, startIndex: Int): Int = {

    val compareWithFirstKey =
      if (curDataBlock != null && curDataBlock.index == startIndex)
        curDataBlock.compareWithKeyAndMoveForward(inputKey, curKVOffset, curBuffer)
      else {
        curDataBlock = reader.dataIndex.dataBlock(0)
        setCurBuffer()
        reader.dataIndex.compareFirstKey(inputKey, startIndex)
      }

    // special case when the inputKey is smaller than the first key
    if (compareWithFirstKey < 0 ) {
      logger.debug (s"seekToInner: found a key lower than the current key in block $startIndex")
      setCurBuffer()
      -1
    } else {
      val index = reader.getDataBlock(inputKey, startIndex, reader.dataIndex.blockCount - 1)
      if (curDataBlock == null || index != curDataBlock.index) {
        curDataBlock = reader.dataIndex.dataBlock(index)
      }
      setCurBuffer()
      val (offset: Int, exactMatch: Boolean) =
        curDataBlock.getClosestKeyOffset(inputKey, curBuffer, curKVOffset)
      curKVOffset = offset
      if (exactMatch) 0 else 1
    }
  }

  private def seekToInner(splitKey: Seq[ByteBuffer], startIndex: Int): Int = {

    val compareWithFirstKey =
      if (curDataBlock != null && curDataBlock.index == startIndex)
        curDataBlock.compareWithKeyAndMoveForward(splitKey, curKVOffset, curBuffer)
      else {
        curDataBlock = reader.dataIndex.dataBlock(0)
        reader.dataIndex.compareFirstKey(splitKey, startIndex)
      }

    // special case when the inputKey is smaller than the first key
    if (compareWithFirstKey < 0 ) {
      logger.debug (s"seekToInner: found a key lower than the current key in block $startIndex")
      setCurBuffer()
      -1
    } else {
      val index = reader.getDataBlockSplitKey(splitKey, startIndex, reader.dataIndex.blockCount - 1)
      if (curDataBlock == null || index != curDataBlock.index) {
        curDataBlock = reader.dataIndex.dataBlock(index)
      }
      setCurBuffer()
      val (offset: Int, exactMatch: Boolean) =
        curDataBlock.getClosestKeyOffset(splitKey, curBuffer, curKVOffset)
      curKVOffset = offset
      if (exactMatch) 0 else 1
    }
  }

  private def setCurBuffer(): Unit = {
    if (prevDataBlock == null || prevDataBlock.index != curDataBlock.index) {
      logger.trace(
        if (prevDataBlock == null || curDataBlock == null) {
          (s"Calling resetBuffer ${prevDataBlock} != ${curDataBlock}")
        } else {
          (s"Calling resetBuffer ${prevDataBlock.index} != ${curDataBlock.index}")
        }
      )
      curBuffer = curDataBlock.resetBuffer()
      prevDataBlock = curDataBlock
      curKVOffset = 8
    }
  }

}
