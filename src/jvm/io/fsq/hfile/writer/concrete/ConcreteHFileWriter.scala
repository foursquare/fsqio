// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.hfile.writer.concrete

import com.google.common.primitives.UnsignedBytes
import io.fsq.common.scala.Lists.Implicits._
import io.fsq.hfile.common.HFileMetadataKeys
import io.fsq.hfile.writer.service.{CompressionAlgorithm, HFileWriter}
import java.io.{ByteArrayOutputStream, DataOutputStream, IOException, OutputStream}
import java.util.Comparator
import org.apache.hadoop.fs.FSDataOutputStream
import org.apache.hadoop.io.compress.Compressor
import scala.math.Ordering._

sealed abstract class BlockType {
  def value: String
  def writeToStream(out: OutputStream): Unit = { out.write(value.getBytes()) }
}

object DataBlock extends BlockType {
  override val value = "DATABLK*"
}

object IndexBlock extends BlockType {
  override val value = "IDXBLK)+"
}

object TrailerBlock extends BlockType {
  override val value = "TRABLK\"$"
}

case class DataBlockIndexEntry(firstKey: Array[Byte], blockOffset: Long, blockDataSize: Int)

class ConcreteHFileWriter(
    val outputStream: FSDataOutputStream,
    val blockSize: Int,
    val compressionName: String) extends HFileWriter with HFileMetadataKeys with Bytes {
  val comparator: Comparator[Array[Byte]] = UnsignedBytes.lexicographicalComparator()
  val byteOrdering: Ordering[Array[Byte]] = Ordering.fromLessThan[Array[Byte]]((a, b) => comparator.compare(a, b) < 0)

  var lastKeyBuffer: Option[Array[Byte]] = None

  val dataBlockIndex = scala.collection.mutable.ListBuffer[DataBlockIndexEntry]()
  var blockBeginKeyOpt: Option[Array[Byte]] = None
  var blockBeginOffsetOpt: Option[Long] = None

  val fileInfo = scala.collection.mutable.ListBuffer[(Array[Byte], Array[Byte])]()

  var entryCount: Int = 0
  var uniqueKeys: Int = 0
  var totalKeyLength: Long = 0L
  var totalValueLength: Long = 0L

  var dataOutputStream: DataOutputStream = _

  val compressionAlgo: CompressionAlgorithm = ConcreteCompressionAlgorithm.algorithmByName(compressionName)
  var compressorOpt: Option[Compressor] = None

  def bytesToString(bs: Array[Byte]): String = {
    bs.map(b => "%02X".format(b)).mkString
  }

  def isDuplicateKey(key: Array[Byte]): Boolean = {
    if (key.isEmpty) {
      throw new IOException("Key can't be empty")
    }

    lastKeyBuffer.map(lk => {
      val keyCmp = comparator.compare(lk, key)
      if (keyCmp > 0) {
        throw new IOException("Added a key (%s) not lexically larger than previous key (%s)".format(
          bytesToString(key), bytesToString(lk)))
      } else if (keyCmp == 0) {
        true
      } else {
        false
      }
    }).getOrElse(false)
  }

  def newCompressingStream(): Unit = {
    compressorOpt = compressionAlgo.compressorOpt()
    dataOutputStream = new DataOutputStream(compressionAlgo.createCompressionStream(outputStream, compressorOpt))
  }

  def releaseCompressingStream(): Int = {
    if (entryCount != 0) {
      dataOutputStream.flush()
      compressorOpt.foreach(co => compressionAlgo.returnCompressor(co))
      compressorOpt = None
      dataOutputStream.size()
    } else {
      0
    }
  }

  def newBlock(): Unit = {
    blockBeginOffsetOpt = Some(outputStream.getPos())
    newCompressingStream()
    DataBlock.writeToStream(dataOutputStream)
  }

  def finishBlock(): Unit = {
    val bytesWritten = releaseCompressingStream()
    for {
      blockBeginKey <- blockBeginKeyOpt
      blockBeginOffset <- blockBeginOffsetOpt
    } {
      dataBlockIndex.append(DataBlockIndexEntry(
        firstKey = blockBeginKey, blockOffset = blockBeginOffset, blockDataSize = bytesWritten))
    }
    blockBeginKeyOpt = None
    blockBeginOffsetOpt = None
  }

  def checkBlockBoundary(): Unit = {
    if (dataOutputStream.size() >= blockSize) {
      finishBlock()
      newBlock()
    }
  }

  def addKeyValue(key: Array[Byte], value: Array[Byte]): Unit = {
    if (entryCount == 0) {
      newBlock()
    } else {
      val dupKey = isDuplicateKey(key)
      if (!dupKey) {
        checkBlockBoundary()
        uniqueKeys += 1
      }
    }

    dataOutputStream.writeInt(key.length)
    totalKeyLength += key.length
    dataOutputStream.writeInt(value.length)
    totalValueLength += value.length

    dataOutputStream.write(key, 0, key.length)
    dataOutputStream.write(value, 0, value.length)

    if (!blockBeginKeyOpt.isDefined) {
      val newK = new Array[Byte](key.length)
      key.copyToArray(newK)
      blockBeginKeyOpt = Some(newK)
    }

    lastKeyBuffer = Some(key)
    entryCount += 1
  }

  def writeBlockIndex(): Unit = {
    if (dataBlockIndex.nonEmpty) {
      IndexBlock.writeToStream(outputStream)
      for {
        dataBlock <- dataBlockIndex
      } {
        outputStream.writeLong(dataBlock.blockOffset)
        outputStream.writeInt(dataBlock.blockDataSize)
        writeByteArray(dataBlock.firstKey, outputStream)
      }
    }
  }

  def writeFileInfo(): Unit = {
    val distinctValues = fileInfo.toList.reverse.distinctBy(_._1.map(b => "%02X".format(b)).mkString)
    outputStream.writeInt(distinctValues.length)
    for {
      (k, v) <- distinctValues.sortBy(_._1)(byteOrdering)
    } {
      writeByteArray(k, outputStream)
      // They encode the type of the value with a magic byte that maps from bytes -> support value classes. The value
      // for Array[Byte] is 1, and that is the only type we support
      outputStream.writeByte(1)
      writeByteArray(v, outputStream)
    }
  }

  def finishFileInfo(): Unit = {
    lastKeyBuffer.foreach(lastKey => {
      fileInfo.append((LastKeyKey.getBytes(), lastKey))
    })

    // Average key length.
    val avgKeyLen = if (entryCount == 0) {
      0
    } else {
      (totalKeyLength / entryCount).toInt
    }
    fileInfo.append((AverageKeyLengthKey.getBytes(), intToBytes(avgKeyLen)))

    // Average value length.
    val avgValueLen = if (entryCount == 0) {
      0
    } else {
      (totalValueLength / entryCount).toInt
    }
    fileInfo.append((AverageValueLengthKey.getBytes(), intToBytes(avgValueLen)))
    fileInfo.append((NumEntries.getBytes(), intToBytes(entryCount)))
    fileInfo.append((NumUniqueKeys.getBytes(), intToBytes(uniqueKeys)))
    fileInfo.append((TotalKeyLength.getBytes(), longToBytes(totalKeyLength)))
    fileInfo.append((TotalValueLength.getBytes(), longToBytes(totalValueLength)))

    // This is a lie, but makes the output byte-compatible with the old output
    fileInfo.append((ComparatorKey.getBytes(), "org.apache.hadoop.hbase.util.Bytes$ByteArrayComparator".getBytes()))
  }

  def addFileInfo(key: Array[Byte], value: Array[Byte]): Unit = {
    val newK = new Array[Byte](key.length)
    key.copyToArray(newK)

    val newV = new Array[Byte](value.length)
    value.copyToArray(newV)
    fileInfo.append((newK, newV))
  }

  def addFileInfo(key: String, value: String): Unit = {
    addFileInfo(key.getBytes("UTF-8"), value.getBytes("UTF-8"))
  }

  def addFileInfo(key: String, value: Long): Unit = {
    addFileInfo(key.getBytes("UTF-8"), longToBytes(value))
  }

  def writeTrailer(fileInfoOffset: Long, dataBlockIndexOffset: Long): Unit = {
    val baos = new ByteArrayOutputStream()
    val baosDos = new DataOutputStream(baos)

    TrailerBlock.writeToStream(baosDos)
    baosDos.writeLong(fileInfoOffset)
    baosDos.writeLong(dataBlockIndexOffset)
    baosDos.writeInt(dataBlockIndex.length)

    // Meta index offset and count, not supported
    baosDos.writeLong(0L)
    baosDos.writeInt(0)
    // Total size is the block data size + the trailer size (60)
    baosDos.writeLong(dataBlockIndex.map(_.blockDataSize.toLong).sum + 60)
    baosDos.writeInt(entryCount)

    // Compression codec ordinal
    baosDos.writeInt(compressionAlgo.compressionId)

    // Version number
    baosDos.writeInt(1)

    outputStream.write(baos.toByteArray())
  }

  def close(): Unit = {
    finishBlock()

    val fileInfoOffset = outputStream.getPos()
    finishFileInfo()
    writeFileInfo()

    val dataBlockIndexOffset = outputStream.getPos()
    writeBlockIndex()

    writeTrailer(fileInfoOffset, dataBlockIndexOffset)
    outputStream.close()
  }
}

