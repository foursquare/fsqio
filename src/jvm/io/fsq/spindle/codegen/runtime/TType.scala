// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.codegen.runtime

import org.apache.thrift.protocol.{TType => JavaTType}

sealed abstract class TType(val name: String, val value: Byte)

object TType {
  case object BOOL extends TType("BOOL", JavaTType.BOOL)
  case object BYTE extends TType("BYTE", JavaTType.BYTE)
  case object DOUBLE extends TType("DOUBLE", JavaTType.DOUBLE)
  case object ENUM extends TType("ENUM", JavaTType.ENUM)
  case object I16 extends TType("I16", JavaTType.I16)
  case object I32 extends TType("I32", JavaTType.I32)
  case object I64 extends TType("I64", JavaTType.I64)
  case object LIST extends TType("LIST", JavaTType.LIST)
  case object MAP extends TType("MAP", JavaTType.MAP)
  case object SET extends TType("SET", JavaTType.SET)
  case object STOP extends TType("STOP", JavaTType.STOP)
  case object STRING extends TType("STRING", JavaTType.STRING)
  case object STRUCT extends TType("STRUCT", JavaTType.STRUCT)
  case object VOID extends TType("VOID", JavaTType.VOID)
}
