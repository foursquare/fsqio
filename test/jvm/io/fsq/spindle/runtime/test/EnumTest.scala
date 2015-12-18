// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.runtime.test

import io.fsq.spindle.codegen.test.gen._
import io.fsq.spindle.runtime.{KnownTProtocolNames, TProtocolInfo}
import org.apache.thrift.TBase
import org.apache.thrift.transport.{TMemoryBuffer, TTransport}
import org.junit.Assert.assertEquals
import org.junit.Test


class EnumTest {

  @Test
  def testEnumAnnotations() {
    assertEquals(Some("blackmad"), TestEnum.annotations.get("owner"))
    assertEquals(Some("jliszka"), TestEnum.Foo.annotations.get("owner"))
    assertEquals(None, TestEnum.Bar.annotations.get("owner"))
  }

  @Test
  def testSerializeEnumAsString() {
    val s = StructWithNewEnumField.newBuilder
      .anEnum(NewTestEnum.Zero)
      .anEnumList(List(NewTestEnum.One, NewTestEnum.Two))
      .anEnumAsString(NewTestEnum.Zero)
      .anEnumListAsString(List(NewTestEnum.One, NewTestEnum.Two))
      .result()
      .toString
    assertEquals("""{ "anEnum": 0, "anEnumList": [ 1, 2 ], "anEnumAsString": "zero", "anEnumListAsString": [ "one", "two" ] }""", s)
  }

  @Test
  def testUnknownEnum() {
    // Test all 25 possible combinations of src and dst protocol.
    val protocols =
      KnownTProtocolNames.TBinaryProtocol ::
        KnownTProtocolNames.TCompactProtocol ::
        KnownTProtocolNames.TJSONProtocol ::
        KnownTProtocolNames.TBSONProtocol ::
        KnownTProtocolNames.TBSONBinaryProtocol ::
        KnownTProtocolNames.TReadableJSONProtocol ::
        Nil

    for (src <- protocols; dst <- protocols) {
      println("Testing unknown enum value compatibility between: %s -> %s".format(src, dst))
      doTestUnknownEnum(src, dst)
    }
  }

  private def doTestUnknownEnum(srcProtocol: String, dstProtocol: String) {
    val enumStruct = StructWithNewEnumField.newBuilder
      .anEnum(NewTestEnum.Two)
      .anEnumList(NewTestEnum.Zero :: NewTestEnum.Two :: NewTestEnum.One :: Nil)
      .anEnumAsString(NewTestEnum.Two)
      .anEnumListAsString(NewTestEnum.Zero :: NewTestEnum.Two :: NewTestEnum.One :: Nil)
      .result()

    // Write the object out.
    val buf = doWrite(srcProtocol, enumStruct)

    // Read it back in at the same version.
    val roundtrippedEnumStruct = StructWithNewEnumField.createRawRecord.asInstanceOf[TBase[_, _]]
    doRead(srcProtocol, buf, roundtrippedEnumStruct)

    // Check that we got what we expect.
    assertEquals(enumStruct, roundtrippedEnumStruct)

    // Now read the new object into an older version of the same struct.
    val buf2 = doWrite(srcProtocol, enumStruct)
    val oldStruct = StructWithOldEnumField.createRawRecord
    val oldObj = oldStruct.asInstanceOf[TBase[_, _]]
    doRead(srcProtocol, buf2, oldObj)

    assertEquals(OldTestEnum.UnknownWireValue(2), oldStruct.anEnumOrNull)
    assertEquals(OldTestEnum.UnknownWireValue("two"), oldStruct.anEnumAsStringOrNull)

    // Write the old object back out using the other protocol.
    val oldBuf = doWrite(dstProtocol, oldObj)

    // Read it back into a fresh instance of the new version of the struct.
    val roundtrippedEnumStruct2 = StructWithNewEnumField.createRawRecord.asInstanceOf[TBase[_, _]]
    doRead(dstProtocol, oldBuf, roundtrippedEnumStruct2)

    // Check that we got what we expect.
    assertEquals(enumStruct, roundtrippedEnumStruct2)
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
