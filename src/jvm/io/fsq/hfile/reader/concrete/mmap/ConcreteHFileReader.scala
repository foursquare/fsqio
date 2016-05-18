// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.hfile.reader.concrete.mmap

import com.twitter.ostrich.stats.Stats
import io.fsq.common.logging.Logger
import io.fsq.common.scala.Identity._
import io.fsq.hfile.reader.service.{HFileReader, HFileScanner, MsgAndSize}
import java.io.{File, FileInputStream, IOException}
import java.nio.ByteBuffer
import java.nio.channels.FileChannel.MapMode.READ_ONLY
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path
import scala.annotation.tailrec
import scala.collection.JavaConverters._

trait BlockReader extends Function2[Long, Long, ByteBuffer] {
  override def apply(start: Long, length: Long): ByteBuffer
  def close(): Unit
}

case class FileChunk(startOffset: Long, buffer: ByteBuffer) {
  override def toString: String = startOffset + "," + buffer
}

object ConcreteHFileReader {
  @volatile var mmapedBytes: Long = 0
  val MB = 1024 * 1024

  def fromFile(file: File, hfilename: String, maximumMMappedBytes: Option[Long], trackCacheStats: Boolean): ConcreteHFileReader = {

    maximumMMappedBytes.map(m => Stats.addGauge("HFileService.TotalCollectionSizeLimit") { m.toDouble })

    val filePath = file.getPath
    val stream = new FileInputStream(file)
    val channel = stream.getChannel

    val lastOffset = file.length // ???

    val DefaultBlockSize = 1024 * MB // 1GB blocks
    val DefaultBlockOverlap = 8 * MB

    val chunks: List[FileChunk] = {
      val chunksBuilder = List.newBuilder[FileChunk]
      var offset = 0L
      while (offset < lastOffset) {
        val blockSize = Math.min(DefaultBlockSize, lastOffset - offset)
        val startOffset = Math.max(0, offset - DefaultBlockOverlap)
        val endOffset = offset - startOffset + blockSize
        chunksBuilder += FileChunk(startOffset, channel.map(READ_ONLY, startOffset, endOffset))
        offset += blockSize
      }
      chunksBuilder.result()
    }

    val blockReader = new BlockReader with Logger {
      override def apply(startOffset: Long, size: Long): ByteBuffer = {
        val buffer = chunks.find(c => c.startOffset <= startOffset && c.startOffset + c.buffer.limit  >= startOffset + size)
          .map(c => {
            val buffer = c.buffer.slice
            buffer.position((startOffset - c.startOffset).toInt)
            buffer.limit((startOffset - c.startOffset + size).toInt)
            buffer.slice
          })
          .getOrElse ({
            logger.info(s"offset=$startOffset, size=$size not possible for chunks: ${chunks.mkString}")
            channel.map(READ_ONLY, startOffset, size)
          })
        Bytes.copyDirectBufferToHeap(buffer)
      }

      override def close(): Unit = {
        for (chunk <- chunks) {
          Bytes.freeDirectByteBuffer(chunk.buffer)
        }
        channel.close()
      }
    }

    new ConcreteHFileReader(filePath, file.length, hfilename, blockReader, trackCacheStats, true, maximumMMappedBytes)
  }

  def fromFileChannel(file: File, hfilename: String, maximumMMappedBytes: Option[Long], trackCacheStats: Boolean): ConcreteHFileReader = {
    maximumMMappedBytes.map(m => Stats.addGauge("HFileService.TotalCollectionSizeLimit") { m.toDouble })

    val filePath = file.getPath
    val stream = new FileInputStream(file)
    val channel = stream.getChannel

    val lastOffset = file.length // ???

    val blockReader = new BlockReader with Logger {
      override def apply(startOffset: Long, size: Long): ByteBuffer = synchronized {
        channel.position(startOffset)
        val buf = ByteBuffer.allocate(size.toInt)
        var ret = 0
        while (channel.read(buf) != -1 && buf.hasRemaining) {}
        buf.flip
        buf
      }

      override def close(): Unit = {
        channel.close()
      }
    }

    new ConcreteHFileReader(filePath, file.length, hfilename, blockReader, trackCacheStats, true, maximumMMappedBytes)
  }

  def fromHadoopPath(path: Path, conf: Configuration): ConcreteHFileReader = {
    val remoteFS = path.getFileSystem(conf)
    val len = remoteFS.getFileStatus(path).getLen
    val stream = remoteFS.open(path)

    val blockReader = new BlockReader {
      override def apply(start: Long, length: Long): ByteBuffer = {
        if (length > Int.MaxValue) {
          throw new IllegalArgumentException("Can't map a ByteBuffer that big: %d, %d".format(start, length))
        }
        val arr = new Array[Byte](length.toInt)
        val bytesRead = stream.read(start, arr, 0, length.toInt)
        ByteBuffer.wrap(arr, 0, bytesRead)
      }
      override def close(): Unit = {
        stream.close()
      }
    }
    new ConcreteHFileReader(path.toString, len, "hadoop", blockReader, false, false, None)
  }

  def fromBytes(name: String, input: Array[Byte]): ConcreteHFileReader = {
    val blockReader = new BlockReader {
      override def apply(start: Long, length: Long): ByteBuffer = {
        val arr = input.slice(start.toInt, (start + length).toInt)
        ByteBuffer.wrap(arr, 0, arr.length)
      }
      override def close(): Unit = { }
    }
    new ConcreteHFileReader(name, input.length, "bytes", blockReader, false, false, None)
  }

}

class ConcreteHFileReader(
    val filePath: String,
    val fileLength: Long,
    val hfilename: String,
    blockReader: BlockReader,
    val trackCacheStats: Boolean,
    val canEvictPages: Boolean,
    val maximumMMappedBytesOpt: Option[Long]) extends HFileReader with Logger {
  val TrailerSize = 60
  val trailerStart: Long = fileLength - TrailerSize
  val trailerBuffer: ByteBuffer = blockReader(trailerStart, TrailerSize)

  val version = new Version(trailerBuffer)
  if (version.majorVersion != 1 && version.minorVersion != 0)
    throw new Exception("wrong version")


  val trailer = new Trailer(trailerBuffer)

  var process: Option[Process] = None
  var isClosed = false
  val dataIndex = {
    val dataIndexEnd: Long = if (trailer.metaIndexOffset =? 0) trailerStart else trailer.metaIndexOffset
    val dataIndexSize = (dataIndexEnd - trailer.dataIndexOffset).toInt
    if (dataIndexSize > 0) {
      val buffer: ByteBuffer = blockReader(trailer.dataIndexOffset, dataIndexSize)
      new DataIndex(blockReader, buffer, dataIndexSize, trailer.dataIndexCount, trailer.compressionCodec, trailer.fileInfoOffset, trackCacheStats)
    } else {
      null
    }
  }

  lazy val fileInfo = {
    val fileInfoSize = trailer.dataIndexOffset-trailer.fileInfoOffset
    val buffer: ByteBuffer = blockReader(trailer.fileInfoOffset, fileInfoSize)
    FileInfo.get(buffer)
  }

  def execProcess(cmd: String): Process = {
    val args = cmd.split("""\s+""")
    val pb = new ProcessBuilder(args: _*)
    logger.info("execProcess: " + cmd)
    pb.inheritIO
    pb.start
  }


  def close(evict: Boolean): Unit = {
    try {
      blockReader.close
      // kill vmtouch process
      process.map(p => {
        p.destroy
        logger.info("waitFor killed process:" + p.waitFor)
        ConcreteHFileReader.synchronized { ConcreteHFileReader.mmapedBytes -= fileLength }
      })
      // vmtouch -e <hfile> to evict all pages
      if (canEvictPages && evict) {
        logger.info("evict: return code" + execProcess(s"./bin/vmtouch -m 100G -e $filePath").waitFor)
        logger.info(s"Ejected $filePath with size $fileLength. ConcreteHFileReader.mmapedBytes = ${ConcreteHFileReader.mmapedBytes}")
      } else {
        logger.info(s"Not ejecting hfile blocks from memory. canEvictPages = $canEvictPages. evict = $evict")
      }
    } catch { case e: IOException => logger.error("Could not close hfile $hfilename: $e") }
    isClosed = true
  }

  def loadIntoMemory(processIsStarting: Boolean): MsgAndSize = {
    // vmtouch -t <hfile> to load into memory
    maximumMMappedBytesOpt.map(limit =>
      if ((ConcreteHFileReader.mmapedBytes + fileLength) > limit) {
        throw new Exception (s"Total mmapped memory about to exceed the limit of $limit bytes. current size = ${ConcreteHFileReader.mmapedBytes} bytes, new file = $fileLength bytes.")
      })

    logger.info("touch: return code " + execProcess(s"./bin/vmtouch -m 100G -t $filePath").waitFor)
    ConcreteHFileReader.synchronized { ConcreteHFileReader.mmapedBytes += fileLength }
    // vmtouch -l <hfile> to prevent evictions
    process = Some(execProcess(s"./bin/vmtouch -m 100G -l $filePath"))
    MsgAndSize(s"Loaded $filePath with size $fileLength. ConcreteHFileReader.mmapedBytes = ${ConcreteHFileReader.mmapedBytes}",
      fileLength/ConcreteHFileReader.MB)
  }


  // get the closest dataBlock with it's firstKey <= key
  @tailrec
  final def getDataBlock(key: ByteBuffer, min: Int, max: Int, lastDataBlockIndex: Int = -1): Int = {
    if (max < min) {
      if (lastDataBlockIndex == -1) { throw new Exception("key not found") }
      logger.debug (s"min = $min")
      lastDataBlockIndex
    } else {
      val mid = min + ((max - min) / 2 )
      val res = dataIndex.compareFirstKey(key, mid)

      if (res == 0) {
        logger.debug(s"mid = $mid")
        mid
      } else if (res > 0) {
        getDataBlock(key, mid + 1, max, mid)
      } else {
        getDataBlock(key, min, mid - 1, lastDataBlockIndex)
      }
    }
  }

  @tailrec
  final def getDataBlockSplitKey(
    splitKey: Seq[ByteBuffer],
    min: Int,
    max: Int,
    lastDataBlockIndex: Int = -1
  ): Int = {
    if (max < min) {
      if (lastDataBlockIndex == -1) { throw new Exception("key not found") }
      logger.debug (s"min = $min")
      lastDataBlockIndex
    } else {
      val mid = min + ((max - min) / 2 )
      val res = dataIndex.compareFirstKey(splitKey, mid)

      if (res == 0) {
        logger.debug(s"mid = $mid")
        mid
      } else if (res > 0) {
        getDataBlockSplitKey(splitKey, mid + 1, max, mid)
      } else {
        getDataBlockSplitKey(splitKey, min, mid - 1, lastDataBlockIndex)
      }
    }
  }

  def getEntries(): Long = {
    trailer.entryCount
  }

  def getFirstKey(): Array[Byte] = {
    Bytes.getByteArray(dataIndex.firstKey)
  }

  def getLastKey(): Array[Byte] = {
    fileInfo
      .asScala
      .toMap
      .map { case (k,v) => k.toSeq -> v }
      .getOrElse("hfile.LASTKEY".getBytes("UTF-8"), null)
 }

  // Needs to be different for readers with the same `hfilename` but different files for hfile updates to work correctly
  // in HFileServerImpl.  Returns the last component of `filePath`, which is what the hbase reader does.
  def getName(): String = {
    filePath.split("/").last
  }

  def getScanner: HFileScanner = {
    new ConcreteHFileScanner(this)
  }

  def getTotalUncompressedBytes(): Long = {
    trailer.totalUncompressedDataBytes
  }

  def getTrailerInfo(): String = {
      "compressionCodec=" + trailer.compressionCodecName +
      ", dataIndexCount=" + trailer.dataIndexCount +
      ", entryCount=" + trailer.entryCount +
      ", fileinfoOffset=" + trailer.fileInfoOffset +
      ", loadOnOpenDataOffset=" + trailer.dataIndexOffset +
      ", majorVersion=" + version.majorVersion +
      ", metaIndexCount=" + trailer.metaIndexCount +
      ", minorVersion=" + version.minorVersion +
      ", totalUncompressedBytes=" + trailer.totalUncompressedDataBytes
  }

  def indexSize: Long = {
    dataIndex.size + dataIndex.heapSize(trailer.metaIndexCount)
  }

  def length(): Long = {
    fileLength
  }

  def loadFileInfo(): java.util.Map[Array[Byte], Array[Byte]] = {
    fileInfo
  }
}
