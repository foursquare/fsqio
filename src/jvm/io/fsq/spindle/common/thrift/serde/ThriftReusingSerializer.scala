// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.common.thrift.serde

import org.apache.thrift.{TBase, TByteArrayOutputStream, TFieldIdEnum}
import org.apache.thrift.protocol.TCompactProtocol
import org.apache.thrift.transport.TIOStreamTransport

/**
  * Custom serializer that reuses the internal byte array instead of defensively copying.
  * NOTE: This means that you *must* use/copy the array before calling.
  *
  * As with the standard TSerializer, this is not threadsafe.
  */
class ThriftReusingSerializer[T <: TBase[_ <: TBase[_, _], _ <: TFieldIdEnum]] {
  private[this] val protocolFactory = new TCompactProtocol.Factory()
  private[this] val baos = new TByteArrayOutputStream(512)
  private[this] val transport = new TIOStreamTransport(baos)
  private[this] val prot = protocolFactory.getProtocol(transport)

  /**
    * Serialize the record out. Remember, the array is reused so you may not retain a reference between calls.
    */
  def serialize(t: T): (Array[Byte], Int) = {
    baos.reset()
    t.write(prot)
    (baos.get, baos.len)
  }
}
