// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.hfile.reader.concrete.mmap

import java.nio.ByteBuffer
import scala.collection.JavaConverters._
import scala.collection.mutable.HashMap

object FileInfo {
  def get(buffer: ByteBuffer): java.util.Map[Array[Byte], Array[Byte]] = {
    val map = new HashMap[Array[Byte], Array[Byte]]()

    val entryCount = buffer.getInt()

    for (i <- 0 until entryCount) {
      val key = Bytes.getByteArray(Bytes.readVarlenByteSeq(buffer))
      val id = buffer.get()
      val value = Bytes.getByteArray(Bytes.readVarlenByteSeq(buffer))
      map += (key -> value)
    }

    map.asJava
  }
}
