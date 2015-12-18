// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.__shaded_for_spindle_bootstrap__.runtime

import java.nio.ByteBuffer
import org.apache.thrift.TBase
import org.apache.thrift.protocol.{TField, TProtocol, TStruct, TType}
import org.apache.thrift.transport.TMemoryInputTransport


// Some protocols are "robust", i.e., they have complete field id and type information on the wire.
// Others are not. For example, TBSONProtocol is not robust: it uses field names instead of ids on
// the wire, and it represents an i16 as an i32, because BSON has no 16-bit integer type.
//
// If we read unknown fields using a non-robust protocol then we may not have enough information to
// write them back out in some other protocol.
//
// So, let's say we have some unknown fields that we read from protocol P1. There are two ways to serialize
// them back out to the wire using protocol P2:
//
//   - Inline: If P1==P2, or P1 was robust, then we can emit the unknown fields in P2's regular stream of fields.
//             The resulting output is as if the fields were known all along. When the P2 stream is later
//             read back in, nothing special needs to happen.
//
//   - As a blob: We serialize the unknown fields to a byte array using P1, and then emit that blob (together
//                with the name of P1, so we know how to interpret the blob) as the value of a "magic" field in P2.
//                Since we read the fields using P1, we can safely write them using P1. However when the P2 stream
//                is later read back in, we need some special handling to unravel the blob.


object UnknownFieldsBlob {
  // The name and identifier of the magic field. We rely on the following to avoid collisions:
  //  - The magic name is obscure, and so unlikely to collide with a real one.
  //  - Field ids in thrift IDLs are required to be positive, so this magic id won't
  //    collide. We don't use -1 because that's occasionally used as a sentinel value.
  val magicField = new TField("__spindle_unknown_fields_blob", TType.STRUCT, -2)

  // The value of the magic field is itself a struct containing two fields: the protocol used to
  // serialize the blob, and the blob itself.
  //
  // It would be nice if that struct could be a real spindle struct generated from a .thrift file,
  // but that creates nasty bootstrapping issues, so we read/write it manually instead, using these fields:
  val unknownFieldsProtocol = new TField("protocol", TType.STRING, 1)
  val unknownFieldsContents = new TField("contents", TType.STRING, 2)

  // Turns the unknown fields to a blob. Matches UnknownFieldsBlob.read().
  def toBlob(unknownFields: UnknownFields): UnknownFieldsBlob = {
    val protocolName: String = unknownFields.inputProtocolName
    val trans = new org.apache.thrift.transport.TMemoryBuffer(1024)
    val oprotFactory = TProtocolInfo.getWriterFactory(protocolName)
    val oprot = oprotFactory.getProtocol(trans)
    oprot.writeStructBegin(new TStruct(""))
    unknownFields.writeInline(oprot)
    oprot.writeFieldStop()
    oprot.writeStructEnd()
    new UnknownFieldsBlob(protocolName, ByteBuffer.wrap(trans.getArray, 0, trans.length))
  }

  // Reads the contents of the magic field from the wire.  Assumes the field header has
  // already been consumed. Matches UnknownFieldsBlob.write().
  def fromMagicField(iprot: TProtocol): UnknownFieldsBlob = {
    iprot.readStructBegin()
    iprot.readFieldBegin()
    val protocolName = iprot.readString()
    iprot.readFieldEnd()
    iprot.readFieldBegin()
    val buf: ByteBuffer = iprot.readBinary()
    iprot.readFieldEnd()
    iprot.readFieldBegin()  // Consume the field stop.
    iprot.readStructEnd()

    new UnknownFieldsBlob(protocolName, buf)
  }
}


// A set of unknown fields, serialized as a blob using some protocol.
class UnknownFieldsBlob(protocolName: String, contents: ByteBuffer) {
  // Write this blob out to the magic field.
  // Matches UnknownFieldsBlob.fromMagicField().
  def write(oprot: TProtocol) {
    oprot.writeFieldBegin(UnknownFieldsBlob.magicField)

    // Write the value struct manually.
    oprot.writeStructBegin(new TStruct(""))
    oprot.writeFieldBegin(UnknownFieldsBlob.unknownFieldsProtocol)
    oprot.writeString(protocolName)
    oprot.writeFieldEnd()
    oprot.writeFieldBegin(UnknownFieldsBlob.unknownFieldsContents)
    oprot.writeBinary(contents)
    oprot.writeFieldEnd()
    oprot.writeFieldStop()
    oprot.writeStructEnd()

    oprot.writeFieldEnd()
  }

  // Let rec attempt to read out of the blob. Fields that are known to rec will be read in as usual,
  // and the rest will go into rec's unknown fields.  Matches UnknownFieldsBlob.toBlob().
  def read(rec: TBase[_, _]) {
    val iprotFactory = TProtocolInfo.getReaderFactory(protocolName)
    val buf = contents.array
    val pos = contents.arrayOffset + contents.position
    val length = contents.limit - contents.position
    val iprot = iprotFactory.getProtocol(new TMemoryInputTransport(buf, pos, length))
    // Note that this call will happen inside an outer call to rec's read().
    rec.read(iprot)
  }
}
