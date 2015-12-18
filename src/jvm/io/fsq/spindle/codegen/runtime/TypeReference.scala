// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.codegen.runtime

sealed trait TypeReference
sealed trait PrimitiveReference extends TypeReference

// primitives for purposes of constants
case object BoolRef extends PrimitiveReference
case object I16Ref extends PrimitiveReference
case object I32Ref extends PrimitiveReference
case object I64Ref extends PrimitiveReference
case object DoubleRef extends PrimitiveReference
case object StringRef extends PrimitiveReference

case object ByteRef extends TypeReference
case object BinaryRef extends TypeReference
case class ListRef(elementType: TypeReference) extends TypeReference
case class SetRef(elementType: TypeReference) extends TypeReference
case class MapRef(keyType: TypeReference, valueType: TypeReference) extends TypeReference
case class EnumRef(name: String) extends TypeReference
case class StructRef(name: String) extends TypeReference
case class UnionRef(name: String) extends TypeReference
case class ExceptionRef(name: String) extends TypeReference
case class ServiceRef(name: String) extends TypeReference
case class TypedefRef(name: String, tpe: TypeReference) extends TypeReference
case class NewtypeRef(name: String, tpe: TypeReference) extends TypeReference
case class EnhancedTypeRef(name: String, tpe: TypeReference) extends TypeReference
case class BitfieldRef(structName: String, bitType: TypeReference, hasSetBits: Boolean) extends TypeReference
