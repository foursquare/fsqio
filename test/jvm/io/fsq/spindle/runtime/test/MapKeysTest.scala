// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.runtime.test

import io.fsq.spindle.codegen.runtime.map_keys.test.gen.MapWithI32Keys
import io.fsq.spindle.common.thrift.base.NonStringMapKeyException
import io.fsq.spindle.runtime.{KnownTProtocolNames, TProtocolInfo}
import org.apache.thrift.transport.TMemoryBuffer
import org.junit.Test


class MapKeysTest {

  @Test(expected=classOf[NonStringMapKeyException])
  def testBSONStringOnlyMapKeys() {
    doTestStringOnlyMapKeys(KnownTProtocolNames.TBSONProtocol)
  }

  @Test(expected=classOf[NonStringMapKeyException])
  def testTReadableJSONStringOnlyMapKeys() {
    doTestStringOnlyMapKeys(KnownTProtocolNames.TReadableJSONProtocol)
  }

  private def doTestStringOnlyMapKeys(tproto: String) {
    val struct = MapWithI32Keys.newBuilder
      .foo(Map(123 -> "A", 456 -> "B"))
      .result()

    // Attempt to write the object out.
    val protocolFactory = TProtocolInfo.getWriterFactory(tproto)
    val trans = new TMemoryBuffer(1024)
    val oprot = protocolFactory.getProtocol(trans)
    struct.write(oprot)  // Expected to throw NonStringMapKeyException.
  }
}
