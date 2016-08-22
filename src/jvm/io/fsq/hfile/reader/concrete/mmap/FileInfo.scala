// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.hfile.reader.concrete.mmap

import io.fsq.common.base.Hex
import io.fsq.common.scala.Identity._
import java.nio.ByteBuffer
import scala.collection.JavaConverters._
import scala.collection.mutable.HashMap

object FileInfo {

  private def getInternal(buffer: ByteBuffer): HashMap[Array[Byte], Array[Byte]] = {
    val map = new HashMap[Array[Byte], Array[Byte]]()

    val entryCount = buffer.getInt()

    for (i <- 0 until entryCount) {
      val key = Bytes.getByteArray(Bytes.readVarlenByteSeq(buffer))
      val id = buffer.get()
      val value = Bytes.getByteArray(Bytes.readVarlenByteSeq(buffer))
      map += (key -> value)
    }

    map
  }

  def get(buffer: ByteBuffer): java.util.Map[Array[Byte], Array[Byte]] = {
    getInternal(buffer).asJava
  }

  def getDeserializedStringMap(buffer: ByteBuffer): Map[String, String] = {
    deserializeToStringMap(getInternal(buffer).toMap)
  }

  def deserializeToStringMap(map: Map[Array[Byte], Array[Byte]]): Map[String, String] = {
    map.map({
      case (k, v) => {
        val key = new String(k, "UTF-8")
        val value = {
          if (v.length =? 4) ByteBuffer.wrap(v).getInt.toString
          else if (v.length =? 8) ByteBuffer.wrap(v).getLong.toString
          else if (v.sameElements(new String(v, "UTF-8").getBytes("UTF-8"))) new String(v, "UTF-8")
          else "0x%s".format(Hex.toHexString(v))
        }
        key -> value
      }
    })
  }


}
