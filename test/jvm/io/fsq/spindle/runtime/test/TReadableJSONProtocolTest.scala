// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.runtime.test

import com.foursquare.common.thrift.json.TReadableJSONProtocol
import io.fsq.spindle.codegen.test.gen.{BinaryStruct, TestStruct, TestStructNoUnknownFieldsTracking}
import io.fsq.spindle.runtime.{MetaRecord, Record}
import java.nio.ByteBuffer
import org.apache.thrift.{TBase, TDeserializer}
import org.apache.thrift.transport.{TMemoryBuffer, TMemoryInputTransport}
import org.bson.types.ObjectId
import org.junit.Assert.assertEquals
import org.junit.Test

class TReadableJSONProtocolTest {

  @Test
  def testNullValues {
    val t1 = deserializeJson("""{"aString":null, "unknown": null}""", TestStruct)
    assertEquals(None, t1.aStringOption)

    val t2 = deserializeJson("""{"aString":null, "unknown": null}""", TestStructNoUnknownFieldsTracking)
    assertEquals(None, t2.aStringOption)
  }

  def deserializeJson[R <: Record[R] with TBase[R, _ <: org.apache.thrift.TFieldIdEnum]](s: String, recMeta: MetaRecord[R, _]): R = {
    val deserializer = new TDeserializer(new TReadableJSONProtocol.Factory())
    val rec = recMeta.createRecord
    deserializer.deserialize(rec, s.getBytes("UTF-8"))
    rec
  }

  @Test
  def testBadBase64 {
    val t1 = readJson("""{"unknown": "aaaaa="}""", BinaryStruct, false)
    assertEquals(None, t1.aBinaryOption)
  }

  @Test
  def testBinarySerialization {
    val oid = new ObjectId("654321abcdef090909fedcba")
    val binary = ByteBuffer.wrap(Array[Byte](1, 2, 3, 4, 5))
    val bs = BinaryStruct.newBuilder.anObjectId(oid).aBinary(binary).result()

    val js1 = writeJson(bs, false)
    val rs1 = readJson(js1, BinaryStruct, false)
    assertEquals(bs, rs1)
    assertEquals(js1, """{
  "anObjectId" : "ObjectId(\"654321abcdef090909fedcba\")",
  "aBinary" : "AQIDBAU="
}""")

    val js2 = writeJson(bs, true)
    val rs2 = readJson(js2, BinaryStruct, true)
    assertEquals(bs, rs2)
    assertEquals(js2, """{
  "anObjectId" : "654321abcdef090909fedcba",
  "aBinary" : "Base64(\"AQIDBAU=\")"
}""")
  }

  @Test
  def testLongParsing {
    val t1 = deserializeJson("""{"anI64":72057594043056517}""", TestStruct)
    assertEquals(Some(72057594043056517L), t1.anI64Option)
  }

  def readJson[R <: Record[R] with TBase[R, _]](s: String, recMeta: MetaRecord[R, _], bareObjectIds: Boolean): R = {
    val protocolFactory = new TReadableJSONProtocol.Factory(false, bareObjectIds)
    val buf = s.getBytes("UTF-8")
    val trans = new TMemoryInputTransport(buf)
    val iprot = protocolFactory.getProtocol(trans)
    val rec = recMeta.createRecord
    rec.read(iprot)
    rec
  }

  def writeJson[R <: Record[R] with TBase[R, _]](rec: R, bareObjectIds: Boolean): String = {
    val protocolFactory = new TReadableJSONProtocol.Factory(false, bareObjectIds)
    val trans = new TMemoryBuffer(1024)
    val oprot = protocolFactory.getProtocol(trans)
    rec.write(oprot)
    JsonPrettyPrinter.prettify(trans.toString("UTF8"))
  }
}
