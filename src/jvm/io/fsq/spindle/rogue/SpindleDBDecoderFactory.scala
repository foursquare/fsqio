// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.rogue

import com.mongodb.{DBCallback, DBCollection, DBDecoder, DBDecoderFactory, DBObject}
import io.fsq.spindle.runtime.{UntypedMetaRecord, UntypedRecord}
import java.io.{ByteArrayInputStream, InputStream}
import org.bson.{BSONCallback, BSONObject}

case class SpindleDBDecoderFactory(
  meta: UntypedMetaRecord,
  recordReader: (UntypedMetaRecord, InputStream) => SpindleDBObject
) extends DBDecoderFactory {
  def create(): DBDecoder = new SpindleDBDecoder(meta, recordReader)
}

case class SpindleDBDecoder(
  meta: UntypedMetaRecord,
  recordReader: (UntypedMetaRecord, InputStream) => SpindleDBObject
) extends DBDecoder {
  def decode(is: InputStream, collection: DBCollection): DBObject = {
    recordReader(meta, is)
  }
  def decode(bytes: Array[Byte], collection: DBCollection): DBObject = {
    decode(new ByteArrayInputStream(bytes), collection)
  }

  def getDBCallback(collection: DBCollection): DBCallback = {
    throw new UnsupportedOperationException()
  }
  def readObject(bytes: Array[Byte]): BSONObject = {
    throw new UnsupportedOperationException()
  }
  def readObject(in: InputStream): BSONObject = {
    throw new UnsupportedOperationException()
  }
  def decode(bytes: Array[Byte], callback: BSONCallback): Int = {
    throw new UnsupportedOperationException()
  }
  def decode(in: InputStream, callback: BSONCallback): Int = {
    throw new UnsupportedOperationException()
  }
}

class SpindleDBObject(val record: UntypedRecord, underlying: DBObject) extends DBObject {

  override def isPartialObject(): Boolean = false
  override def markAsPartialObject(): Unit = {
    underlying.markAsPartialObject()
  }

  override def get(key: String): Object = underlying.get(key)

  override def put(key: String, dv: Object): Object = throw new UnsupportedOperationException()
  override def putAll(o: BSONObject): Unit = throw new UnsupportedOperationException()
  override def putAll(m: java.util.Map[_, _]): Unit = throw new UnsupportedOperationException()
  override def toMap(): java.util.Map[_, _] = throw new UnsupportedOperationException()
  override def removeField(key: String): Object = throw new UnsupportedOperationException()
  override def containsKey(s: String): Boolean = throw new UnsupportedOperationException()
  override def containsField(s: String): Boolean = throw new UnsupportedOperationException()
  override def keySet(): java.util.Set[String] = throw new UnsupportedOperationException()
}
