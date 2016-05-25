// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.hfile.writer.concrete

import io.fsq.hfile.writer.service.CompressionAlgorithm
import java.io.{BufferedOutputStream, FilterOutputStream, OutputStream}
import org.apache.hadoop.conf.{Configurable, Configuration}
import org.apache.hadoop.io.compress.{CodecPool, CompressionCodec, CompressionOutputStream, Compressor}
import org.apache.hadoop.util.ReflectionUtils

class FinishOnFlushCompressionStream(val cout: CompressionOutputStream) extends FilterOutputStream(cout) {
  override def write(b: Array[Byte], off: Int, len: Int): Unit = {
    cout.write(b, off, len)
  }

  @Override
  override def flush(): Unit = {
    cout.finish()
    cout.flush()
    cout.resetState()
  }
}

abstract class ConcreteCompressionAlgorithm extends CompressionAlgorithm {
  val conf = new Configuration
  conf.setBoolean("hadoop.native.lib", true)

  val DataOBufSize: Int = 4 * 1024

  def codecOpt(): Option[CompressionCodec]

  def createCompressionStream(downStream: OutputStream, compressorOpt: Option[Compressor]): OutputStream = {
    val compressor = compressorOpt.getOrElse(throw new Exception("no compressor passed to createCompressionStream"))
    val cos = createPlainCompressionStream(downStream, compressor)
    new BufferedOutputStream(new FinishOnFlushCompressionStream(cos), DataOBufSize)
  }

  def createPlainCompressionStream(downStream: OutputStream, compressor: Compressor): CompressionOutputStream = {
    val codec = codecOpt().getOrElse(throw new Exception("no codec received"))
    codec.asInstanceOf[Configurable].getConf().setInt("io.file.buffer.size", 32 * 1024)
    codec.createOutputStream(downStream, compressor)
  }

  def compressorOpt(): Option[Compressor] = {
    codecOpt().map(c => {
      val compressor: Compressor = CodecPool.getCompressor(c)
      if (compressor.finished) {
        compressor.reset()
      }
      compressor
    })
  }

  def returnCompressor(compressor: Compressor): Unit =  {
    CodecPool.returnCompressor(compressor)
  }
}

object ConcreteCompressionAlgorithm {
  def algorithmByName(algorithm: String): CompressionAlgorithm = {
    algorithm match {
      case "none" => new NoneCompressionAlgorithm
      case "snappy" => new SnappyCompressionAlgorithm
      case _ => throw new Exception("compression algorithm '%s' not supported".format(algorithm))
    }
  }
}

class NoneCompressionAlgorithm extends ConcreteCompressionAlgorithm {
  def codecOpt(): Option[CompressionCodec] = None

  override def createCompressionStream(downStream: OutputStream, compressorOpt: Option[Compressor]): OutputStream = {
    new BufferedOutputStream(downStream)
  }

  val compressionName: String = "none"

  val compressionId: Int = 2
}

class SnappyCompressionAlgorithm extends ConcreteCompressionAlgorithm {
  lazy val codecOpt: Option[CompressionCodec] = {
    val externalCodec = ClassLoader.getSystemClassLoader().loadClass("org.apache.hadoop.io.compress.SnappyCodec")
    Some(ReflectionUtils.newInstance(externalCodec, conf).asInstanceOf[CompressionCodec])
  }

  val compressionName: String = "snappy"

  val compressionId: Int = 3
}

