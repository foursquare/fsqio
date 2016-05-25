// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.hfile.writer.service

import java.io.OutputStream
import org.apache.hadoop.io.compress.Compressor

trait CompressionAlgorithm {
  def createCompressionStream(downStream: OutputStream, compressorOpt: Option[Compressor]): OutputStream

  def compressorOpt(): Option[Compressor]

  def returnCompressor(compressor: Compressor): Unit

  def compressionName: String

  def compressionId: Int
}

