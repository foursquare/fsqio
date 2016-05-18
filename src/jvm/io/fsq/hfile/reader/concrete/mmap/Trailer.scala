// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.hfile.reader.concrete.mmap

import io.fsq.common.scala.Identity._
import java.nio.ByteBuffer
import java.util.Arrays

class Trailer(buffer: ByteBuffer) {
  val magic = new Array[Byte](8)
  buffer.get(magic)

  if (Arrays.equals(magic, "TRABLK\"$".getBytes()) !=? true)
    throw new Exception("bad header magic")

  val fileInfoOffset = buffer.getLong()
  val dataIndexOffset = buffer.getLong()
  val dataIndexCount = buffer.getInt()
  val metaIndexOffset = buffer.getLong()
  val metaIndexCount = buffer.getInt()
  val totalUncompressedDataBytes = buffer.getLong()
  val entryCount = buffer.getInt()
  val compressionCodec = buffer.getInt()
  val compressionCodecNames = Array("LZO", "GZ", "NONE", "SNAPPY", "LZ4")
  val compressionCodecName = compressionCodecNames(compressionCodec)
}
