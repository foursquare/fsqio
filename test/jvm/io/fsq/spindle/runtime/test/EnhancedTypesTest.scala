// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.runtime.test

import io.fsq.spindle.codegen.test.gen.{BSONObjectFields, ObjectIdFields, UUIDFields}
import io.fsq.spindle.runtime.{KnownTProtocolNames, TProtocolInfo}
import java.util.UUID
import org.apache.thrift.TBase
import org.apache.thrift.transport.TMemoryBuffer
import org.bson.BasicBSONObject
import org.bson.types.ObjectId
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import scala.collection.JavaConverters._

@RunWith(value = classOf[Parameterized])
class EnhancedTypesTest(tproto: String) {

  private def roundTripTest[T <: TBase[_, _]](source: T, dest: T): Unit = {
    val trans = new TMemoryBuffer(1024)
    val writeProtocol = {
      val protocolFactory = TProtocolInfo.getWriterFactory(tproto)
      protocolFactory.getProtocol(trans)
    }
    source.write(writeProtocol)
    val readProtocol = {
      val protocolFactory = TProtocolInfo.getReaderFactory(tproto)
      protocolFactory.getProtocol(trans)
    }
    dest.read(readProtocol)
    assertEquals(source, dest)
  }

  @Test
  def objectIdFields(): Unit = {
    val src = ObjectIdFields.newBuilder
      .foo(new ObjectId())
      .bar(new ObjectId() :: new ObjectId() :: Nil)
      .baz(Map("A" -> new ObjectId(), "B" -> new ObjectId()))
      .result()

    roundTripTest(src, ObjectIdFields.createRawRecord)
  }


  @Test
  def bsonObjectFields(): Unit = {
    val bso = new BasicBSONObject()
    bso.put("foo", "bar")

    val src = BSONObjectFields.newBuilder
      .bso(bso)
      .result()

    val dest = BSONObjectFields.createRawRecord

    roundTripTest(src, dest)

    assertEquals(src.bsoOrNull.get("foo"), "bar")
    assertEquals(dest.bsoOrNull.get("foo"), "bar")
  }

  @Test
  def uuidFields(): Unit = {
    val src = UUIDFields.newBuilder
      .qux(UUID.fromString("cba096a8-2e96-4668-9308-3086591201a7"))
      .quux(Vector(
        UUID.fromString("14edb439-75e3-4cd8-9175-b4460815670e"),
        UUID.fromString("d86405f4-0003-44af-96d9-22368e53f116")
      ))
      .norf(Map(
        "A" -> UUID.fromString("31b0db7d-2de7-4aa7-b3c1-a07d649f5770"),
        "B" -> UUID.fromString("9a3685fd-b2ef-4401-b9bc-c1849c280499")))
      .result()

    roundTripTest(src, UUIDFields.createRawRecord)
  }
}

object EnhancedTypesTest {

  @Parameters(name = "tproto={0}")
  def parameters: java.util.List[String] = {
    Vector(
      KnownTProtocolNames.TBinaryProtocol,
      KnownTProtocolNames.TCompactProtocol,
      KnownTProtocolNames.TJSONProtocol,
      KnownTProtocolNames.TBSONProtocol,
      KnownTProtocolNames.TBSONBinaryProtocol,
      KnownTProtocolNames.TReadableJSONProtocol
    ).asJava
  }
}

