// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.runtime.test

import io.fsq.spindle.codegen.test.gen.{BSONObjectFields, ObjectIdFields, UUIDFields}
import io.fsq.spindle.runtime.{KnownTProtocolNames, TProtocolInfo}
import java.util.UUID
import org.apache.thrift.TBase
import org.apache.thrift.transport.{TMemoryBuffer, TTransport}
import org.bson.BasicBSONObject
import org.bson.types.ObjectId
import org.junit.Assert.assertEquals
import org.junit.Test


class EnhancedTypesTest {

  @Test
  def testObjectIdFields() {
    val protocols =
      KnownTProtocolNames.TBinaryProtocol ::
      KnownTProtocolNames.TCompactProtocol ::
      KnownTProtocolNames.TJSONProtocol ::
      KnownTProtocolNames.TBSONProtocol ::
      KnownTProtocolNames.TBSONBinaryProtocol ::
      KnownTProtocolNames.TReadableJSONProtocol ::
      Nil

    for (tproto <- protocols) {
      println("Testing enhanced types for protocol %s".format(tproto))
      doTestObjectIdFields(tproto)
      doTestBSONObjectFields(tproto)
      doTestUUIDFields(tproto)
    }
  }

  private def doTestObjectIdFields(tproto: String) {
    val struct = ObjectIdFields.newBuilder
      .foo(new ObjectId())
      .bar(new ObjectId() :: new ObjectId() :: Nil)
      .baz(Map("A" -> new ObjectId(), "B" -> new ObjectId()))
      .result()

    // Write the object out.
    val buf = doWrite(tproto, struct)

    // Read the new object into an older version of the same struct.
    val roundtrippedStruct = ObjectIdFields.createRawRecord.asInstanceOf[TBase[_, _]]
    doRead(tproto, buf, roundtrippedStruct)

    assertEquals(struct, roundtrippedStruct)
  }

  private def doTestBSONObjectFields(tproto: String) {
    val bso = new BasicBSONObject()
    bso.put("foo", "bar")

    val struct = BSONObjectFields.newBuilder
      .bso(bso)
      .result()

    // Write the object out.
    val buf = doWrite(tproto, struct)

    // Read the new object into an older version of the same struct.
    val bsoStruct= BSONObjectFields.createRawRecord
    val roundtrippedStruct = bsoStruct.asInstanceOf[TBase[_, _]]
    doRead(tproto, buf, roundtrippedStruct)

    assertEquals(struct, roundtrippedStruct)
    assertEquals(struct.bsoOrNull.get("foo"), "bar")
    assertEquals(bsoStruct.bsoOrNull.get("foo"), "bar")
  }

  private def doTestUUIDFields(tproto: String) {
    val struct = UUIDFields.newBuilder
      .qux(UUID.fromString("cba096a8-2e96-4668-9308-3086591201a7"))
      .quux(Vector(
        UUID.fromString("14edb439-75e3-4cd8-9175-b4460815670e"),
        UUID.fromString("d86405f4-0003-44af-96d9-22368e53f116")))
      .norf(Map(
        "A" -> UUID.fromString("31b0db7d-2de7-4aa7-b3c1-a07d649f5770"),
        "B" -> UUID.fromString("9a3685fd-b2ef-4401-b9bc-c1849c280499")))
      .result()

    // Write the object out.
    val buf = doWrite(tproto, struct)

    // Read the new object into an older version of the same struct.
    val roundtrippedStruct = UUIDFields.createRawRecord.asInstanceOf[TBase[_, _]]
    doRead(tproto, buf, roundtrippedStruct)

    assertEquals(struct, roundtrippedStruct)
  }

  private def doWrite(protocolName: String, thriftObj: TBase[_, _]): TMemoryBuffer = {
    val protocolFactory = TProtocolInfo.getWriterFactory(protocolName)
    val trans = new TMemoryBuffer(1024)
    val oprot = protocolFactory.getProtocol(trans)
    thriftObj.write(oprot)
    trans
  }

  private def doRead(protocolName: String, trans: TTransport, thriftObj: TBase[_, _]) {
    val protocolFactory = TProtocolInfo.getReaderFactory(protocolName)
    val iprot = protocolFactory.getProtocol(trans)
    thriftObj.read(iprot)
  }
}
