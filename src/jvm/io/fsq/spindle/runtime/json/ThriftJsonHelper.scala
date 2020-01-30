// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.runtime.json

import io.fsq.json.lift.JsonSerialization
import io.fsq.spindle.common.thrift.json.TReadableJSONProtocol
import net.liftweb.json.{JField, JObject, parse}
import net.liftweb.json.JsonAST.{JArray, JBool, JDouble, JInt, JNothing, JString, JValue}
import org.apache.thrift.TException
import org.apache.thrift.protocol.{TField, TList, TProtocol, TStruct, TType}

object ThriftJsonHelper {
  def getTypeFromJValue(value: JValue): Byte = value match {
    case JBool(_) => TType.BOOL
    case JDouble(_) => TType.DOUBLE
    case JInt(_) => TType.I64
    case JString(_) => TType.STRING
    case JObject(_) => TType.STRUCT
    case JArray(_) => TType.LIST
    case _ => throw new TException("Unexpected JValue %s".format(value.toString))
  }

  def compareJson(jsonA: JValue, jsonB: JValue): Int = {
    JsonSerialization.serialize(jsonA).compareTo(JsonSerialization.serialize(jsonB))
  }

  def writeJson(oprot: TProtocol, json: JValue): Unit = {
    oprot match {
      /* For the TReadableJSONProtocol, we send/receive raw json, while for all other
       * protocols we convert it to a string.
       */
      case jsonProt: TReadableJSONProtocol => {
        writeJValue(oprot, json)
      }
      case _ => {
        oprot.writeString(JsonSerialization.serialize(json))
      }
    }
  }

  def writeJValue(oprot: TProtocol, json: JValue): Unit = json match {
    case JBool(b) => oprot.writeBool(b)
    case JDouble(n) => oprot.writeDouble(n)
    case JInt(n) => oprot.writeI64(n.toLong)
    case JString(s) => oprot.writeString(s)
    case JArray(arr) => {
      // We will use VOID if it is not a homogeneous list.
      val arrayType = {
        arr.map(getTypeFromJValue).distinct match {
          case elemType +: Nil => elemType
          case _ => TType.VOID
        }
      }
      oprot.writeListBegin(new TList(arrayType, arr.size))
      arr.foreach(writeJValue(oprot, _))
      oprot.writeListEnd()
    }
    case JObject(obj) => {
      oprot.writeStructBegin(new TStruct("Json"))
      obj
        .filter(_.value != JNothing)
        .foreach(f => {
          oprot.writeFieldBegin(new TField(f.name, getTypeFromJValue(f.value), 0))
          writeJValue(oprot, f.value)

          oprot.writeFieldEnd()
        })
      oprot.writeFieldStop()
      oprot.writeStructEnd()
    }
    case _ => throw new Exception("Cannot render %s".format(json.toString))
  }

  def writeDateTime(oprot: TProtocol, value: org.joda.time.DateTime) = {
    val out = oprot match {
      case _: TReadableJSONProtocol => value.getMillis / 1000
      case _ => value.getMillis
    }
    oprot.writeI64(out)
  }

  def readDateTime(iprot: TProtocol, value: Long): org.joda.time.DateTime = {
    val millis = iprot match {
      case _: TReadableJSONProtocol => value * 1000
      case _ => value
    }
    new org.joda.time.DateTime(millis)
  }

  def readJson(iprot: TProtocol): JObject = {
    /* For the TReadableJSONProtocol, we send/receive raw json, while for all other
     * protocols we convert it to a string.
     */
    iprot match {
      case jsonProt: TReadableJSONProtocol => {
        readStruct(iprot)
      }
      case _ => {
        val jValue = parse(iprot.readString())
        try {
          jValue.asInstanceOf[JObject]
        } catch {
          case _: Exception => throw new TException("Unexpected JValue parsed: %s".format(jValue.toString))
        }
      }
    }
  }

  def readByType(iprot: TProtocol, thriftType: Byte): JValue = {
    thriftType match {
      case TType.BOOL => JBool(iprot.readBool())
      case TType.DOUBLE => JDouble(iprot.readDouble())
      case TType.I32 => JInt(iprot.readI32)
      case TType.I64 => JInt(iprot.readI64())
      case TType.STRING => JString(iprot.readString())
      case TType.STRUCT => readStruct(iprot)
      case TType.LIST => JArray(readList(iprot).toList)
      case _ => throw new TException("Unexpected type received: %s".format(thriftType.toString))
    }
  }

  def readList(iprot: TProtocol): Seq[JValue] = {
    val tTypes: Seq[Byte] = iprot match {
      // Heterogeneous list support
      case jsonProt: TReadableJSONProtocol => jsonProt.readListBeginEnhanced().toVector
      case _ => {
        // Basic thrift list, which has a single type for all elements in the list
        val tList = iprot.readListBegin()
        val elemType = tList.elemType
        (1 to tList.size).map(x => elemType).toVector
      }
    }
    val elements = tTypes.map(tType => readByType(iprot, tType))
    iprot.readListEnd()

    elements.toVector
  }

  def readStruct(iprot: TProtocol): JObject = {
    iprot.readStructBegin()
    val fieldBuilder = Vector.newBuilder[JField]
    var field_header: TField = iprot.readFieldBegin()
    while (field_header.`type` != TType.STOP) {
      fieldBuilder += JField(field_header.name, readByType(iprot, field_header.`type`))
      iprot.readFieldEnd()
      field_header = iprot.readFieldBegin()
    } // end while
    iprot.readStructEnd()
    JObject(fieldBuilder.result.toList)
  }
}
