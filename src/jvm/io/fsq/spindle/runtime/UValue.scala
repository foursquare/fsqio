// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.runtime

import java.nio.ByteBuffer
import org.apache.thrift.TBase
import org.apache.thrift.protocol.{TField, TList, TMap, TProtocol, TSet, TStruct, TType}


// The value of an unknown field read off the wire.
// There's no field to put it in, so we have to store it along with
// whatever type/field id info the protocol provided on the wire.
trait UValue {
  // Write this value back out, using the specified protocol.
  def write(oprot: TProtocol): Unit
}

case class BoolUValue(value: Boolean) extends UValue {
  def write(oprot: TProtocol) = oprot.writeBool(value)
}

case class ByteUValue(value: Byte) extends UValue {
  def write(oprot: TProtocol) = oprot.writeByte(value)
}

case class I16UValue(value: Short) extends UValue {
  def write(oprot: TProtocol) = oprot.writeI16(value)
}

case class I32UValue(value: Int) extends UValue {
  def write(oprot: TProtocol) = oprot.writeI32(value)
}

case class I64UValue(value: Long) extends UValue {
  def write(oprot: TProtocol) = oprot.writeI64(value)
}

case class DoubleUValue(value: Double) extends UValue {
  def write(oprot: TProtocol) = oprot.writeDouble(value)
}

case class StringUValue(value: String) extends UValue {
  def write(oprot: TProtocol) = oprot.writeString(value)
}

case class BinaryUValue(value: ByteBuffer) extends UValue {
  def write(oprot: TProtocol) = oprot.writeBinary(value)
}

case class StructUValue(tstruct: TStruct, value: UnknownFields) extends UValue {
  def write(oprot: TProtocol) {
    oprot.writeStructBegin(tstruct)
    // It's OK to always write inline, because we're already wrapped in a magic struct, if needed.
    value.writeInline(oprot)
    oprot.writeFieldStop()
    oprot.writeStructEnd()
  }
}

case class SetUValue(tset: TSet, value: Set[UValue]) extends UValue {
  def write(oprot: TProtocol) {
    oprot.writeSetBegin(tset)
    value foreach(_.write(oprot))
    oprot.writeSetEnd()
  }
}
case class ListUValue(tlist: TList, value: Vector[UValue]) extends UValue {
  def write(oprot: TProtocol) {
    oprot.writeListBegin(tlist)
    value foreach(_.write(oprot))
    oprot.writeListEnd()
  }
}

case class MapUValue(tmap: TMap, value: Map[UValue, UValue]) extends UValue {
  def write(oprot: TProtocol) {
    oprot.writeMapBegin(tmap)
    value foreach { x =>
      x._1.write(oprot)
      x._2.write(oprot)
    }
    oprot.writeMapEnd()
  }
}

object UValue {
  // Read the value of an unknown field off the wire.
  def read(rec: TBase[_, _] with Record[_], iprot: TProtocol, fieldType: Byte): UValue = fieldType match {
    case TType.BOOL => BoolUValue(iprot.readBool())
    case TType.BYTE => ByteUValue(iprot.readByte())
    case TType.DOUBLE => DoubleUValue(iprot.readDouble())
    case TType.I16 => I16UValue(iprot.readI16())
    case TType.I32 => I32UValue(iprot.readI32())
    case TType.I64 => I64UValue(iprot.readI64())
    // Note that binary and string both use TType.STRING. In text-based protocols the safe way
    // to read either is as a string, in binary protocols the safe way to read either is as a binary.
    case TType.STRING => TProtocolInfo.isTextBased(TProtocolInfo.getProtocolName(iprot)) match {
      case true => StringUValue(iprot.readString())
      case false => BinaryUValue(iprot.readBinary())
    }
    case TType.STRUCT => {
      val inner = new UnknownFields(rec, TProtocolInfo.getProtocolName(iprot))
      val tstruct: TStruct = iprot.readStructBegin()
      Iterator.continually(iprot.readFieldBegin()).takeWhile(_.`type` != TType.STOP) foreach {
        tfield: TField => {
          inner.readInline(iprot, tfield)
          iprot.readFieldEnd()
        }
      }
      iprot.readStructEnd()
      StructUValue(tstruct, inner)
    }
    case TType.MAP => {
      val tmap: org.apache.thrift.protocol.TMap = iprot.readMapBegin()
      val builder = scala.collection.immutable.Map.newBuilder[UValue, UValue]
      builder.sizeHint(tmap.size)
      var i: Int = tmap.size
      while (i > 0) {
        val k = read(rec, iprot, tmap.keyType)
        val v = read(rec, iprot, tmap.valueType)
        builder += ((k, v))
        i -= 1
      }
      iprot.readMapEnd()
      MapUValue(tmap, builder.result())
    }
    case TType.SET => {
      val tset: org.apache.thrift.protocol.TSet = iprot.readSetBegin()
      val builder = scala.collection.immutable.Set.newBuilder[UValue]
      var i: Int = tset.size
      builder.sizeHint(tset.size)
      while (i > 0) {
        builder += read(rec, iprot, tset.elemType)
        i -= 1
      }
      iprot.readSetEnd()
      SetUValue(tset, builder.result())
    }
    case TType.LIST => {
      val tlist = iprot.readListBegin()
      val builder = scala.collection.immutable.Vector.newBuilder[UValue]
      var i: Int = tlist.size
      builder.sizeHint(tlist.size)
      while (i > 0) {
        builder += read(rec, iprot, tlist.elemType)
        i -= 1
      }
      iprot.readListEnd()
      ListUValue(tlist, builder.result())
    }
    case _ => throw new Exception("Unknown wire type: %s".format(fieldType))
  }
}
