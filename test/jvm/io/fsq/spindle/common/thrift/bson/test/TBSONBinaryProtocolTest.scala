// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.common.thrift.bson.test

import com.mongodb.{BasicDBObjectBuilder, DBObject}
import io.fsq.spindle.codegen.test.gen._
import io.fsq.spindle.common.thrift.bson.{TBSONBinaryProtocol, TBSONObjectProtocol}
import io.fsq.spindle.runtime.UntypedRecord
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.util.Arrays
import org.apache.thrift.TException
import org.bson.BasicBSONEncoder
import org.bson.types.ObjectId
import org.junit._

class TBSONBinaryProtocolTest {

  def writeStructToDboBytes(record: UntypedRecord): Array[Byte] = {
    val protocolFactory = new TBSONObjectProtocol.WriterFactoryForDBObject
    val writeProtocol = protocolFactory.getProtocol
    record.write(writeProtocol)
    val dbo = writeProtocol.getOutput.asInstanceOf[DBObject]
    val encoder = new BasicBSONEncoder()
    encoder.encode(dbo)
  }

  def encodeDboToBytes(dbo: DBObject): Array[Byte] = {
    val encoder = new BasicBSONEncoder()
    encoder.encode(dbo)
  }

  def assertRoundTrip(record: UntypedRecord): Unit = {
    val bytes: Array[Byte] = writeStructToDboBytes(record)
    val newRecord = record.meta.createUntypedRawRecord
    val protocol = new TBSONBinaryProtocol()
    protocol.setSource(new ByteArrayInputStream(bytes))
    newRecord.read(protocol)
    Assert.assertEquals(record, newRecord)
  }

  @Test
  def testBasicFields {

    val struct = TestStruct.newBuilder
      .aByte(12.toByte)
      .anI16(1234.toShort)
      .anI32(123456)
      .anI64(123456789999L)
      .aDouble(123456.123456)
      .aString("hello, how are you today?")
      .aBinary(ByteBuffer.wrap("foobar".getBytes("UTF-8")))
      .aStruct(InnerStruct("inner hello", 1234567))
      .aSet(Set("1","2","3","4","5"))
      .aList(List(1,2,3,4,5))
      .aMap(
        Map("one" -> InnerStruct("inner in map one", 1),
            "two" -> InnerStruct("inner in map two", 2)
        )
      )
      .aMyBinary(ByteBuffer.wrap(Array[Byte](1, 2, 3, 4, 5, 6)))
      .aStructList(List(
        InnerStruct("inner in list one", 1),
        InnerStruct("inner in list one", 2)
      )).result()

    assertRoundTrip(struct)
  }

  @Test
  def testObjectIdListField {
    val oid1 = new ObjectId()
    val oid2 = new ObjectId()
    val dbo: DBObject = BasicDBObjectBuilder.start()
      .add("anObjectIdList", Arrays.asList(oid1, oid2))
      .add("anI32", 123)
      .get

    val newRecord = new RawTestStructOidList()
    val protocol = new TBSONBinaryProtocol()
    protocol.setSource(new ByteArrayInputStream(encodeDboToBytes(dbo)))
    newRecord.read(protocol)
    Assert.assertEquals(List(oid1, oid2), newRecord.anObjectIdList)
    Assert.assertEquals(123, newRecord.anI32)
  }

  @Test
  def testNestedStruct {

    val struct = TestStructNestedCollections.newBuilder
      .listBool(List(List(true, false, true)))
      .listStruct(List(List(
        InnerStruct("inner in list one", 1),
        InnerStruct("inner in list one", 2)
      )))
      .mapBool(Map("outer" -> Map("inner" -> true)))
      .mapStruct(Map("outer" -> Map("inner" -> InnerStruct("inner in list one", 1))))
      .result()

    assertRoundTrip(struct)
  }

  @Test
  def testMongoError {
    val message = "Things have gone very poorly"
    val dbo: DBObject = BasicDBObjectBuilder.start().add("$err", message).add("code", 123).get
    val newRecord = new RawTestStruct()
    val protocol = new TBSONBinaryProtocol()
    protocol.setSource(new ByteArrayInputStream(encodeDboToBytes(dbo)))
    newRecord.read(protocol)
    Assert.assertEquals(message, protocol.errorMessage)
    Assert.assertEquals(123, protocol.errorCode)
  }

  @Test(expected=classOf[TException])
  def testMistypedMongoField {
    val dbo: DBObject = BasicDBObjectBuilder.start().add("aBool", 1).get
    val newRecord = new RawTestStruct()
    val protocol = new TBSONBinaryProtocol()
    protocol.setSource(new ByteArrayInputStream(encodeDboToBytes(dbo)))
    newRecord.read(protocol)
  }

  @Test
  def testDecodeDateAsI64 {
    val date = new java.util.Date()
    val dbo: DBObject = BasicDBObjectBuilder.start().add("anI64", date).get
    val newRecord = new RawTestStruct()
    val protocol = new TBSONBinaryProtocol()
    protocol.setSource(new ByteArrayInputStream(encodeDboToBytes(dbo)))
    newRecord.read(protocol)
    Assert.assertEquals(date.getTime, newRecord.anI64)
  }

  @Test
  def testShouldReadBsonFully {
    val struct = TestStruct.newBuilder
      .aByte(12.toByte)
      .anI16(1234.toShort)
      .aBool(true)
      .result()

    val newRecord = new RawTestStructNoBoolNoUnknownFieldsTracking()
    val protocol = new TBSONBinaryProtocol()
    val is = new ByteArrayInputStream(writeStructToDboBytes(struct))
    protocol.setSource(is)
    newRecord.read(protocol)
    Assert.assertEquals(
      TestStructNoBoolNoUnknownFieldsTracking.newBuilder.aByte(12.toByte).anI16(1234.toShort).result(),
      newRecord
    )
    Assert.assertEquals("Shouldn't have any bytes left even if a field is missing", 0, is.available)
  }

  @Test
  def testNullValues {
    val dbo: DBObject = BasicDBObjectBuilder.start()
      .add("aString", null)
      .add("anI32", null)
      .add("aStruct", null)
      .add("aList", null)
      .add("anI16", 1234)
      .get
    val newRecord = new RawTestStruct()
    val protocol = new TBSONBinaryProtocol()
    protocol.setSource(new ByteArrayInputStream(encodeDboToBytes(dbo)))
    newRecord.read(protocol)
    Assert.assertEquals(None, newRecord.aStringOption)
    Assert.assertEquals(None, newRecord.anI32Option)
    Assert.assertEquals(None, newRecord.aStructOption)
    Assert.assertEquals(None, newRecord.aListOption)
    Assert.assertEquals(1234.toShort, newRecord.anI16)
  }

}
