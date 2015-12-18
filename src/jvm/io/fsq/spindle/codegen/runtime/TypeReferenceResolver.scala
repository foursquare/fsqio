// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.codegen.runtime

import io.fsq.spindle.__shaded_for_spindle_bootstrap__.descriptors.{Annotation, BaseType, ContainerType,
    SimpleBaseType, Type, TypeRegistry, Typeref}
import io.fsq.spindle.__shaded_for_spindle_bootstrap__.runtime.Annotations
import scala.{Either, Right}

sealed trait TypeNotFound
final case class AliasNotFound(typeAlias: String) extends TypeNotFound
final case class TypeIdNotFound(typeId: String) extends TypeNotFound
final case object EnhancedTypeFailure extends TypeNotFound

class TypeReferenceResolver(
    val registry: TypeRegistry,
    val scope: Map[String, TypeDeclaration],
    val enhancedTypes: EnhancedTypes
) {

  def typeForTypeId(typeId: String): Either[TypeNotFound, (Type, Annotations)] = {
    val option = for (tpe <- registry.idToType.get(typeId)) yield (tpe, annotationsForType(tpe))
    optionToEither(option, TypeIdNotFound(typeId))
  }

  def resolveTypeId(typeId: String, extraAnnotations: Annotations): Either[TypeNotFound, TypeReference] = {
    typeForTypeId(typeId).right.flatMap(pair => {
      val (tpe, baseAnnotations) = pair
      resolveType(tpe).right.flatMap(resolvedTpe => {
        val enhancedTpeOption = enhancedTypes(resolvedTpe, baseAnnotations ++ extraAnnotations, scope)
        optionToEither(enhancedTpeOption, EnhancedTypeFailure)
      })
    })
  }

  def resolveTypeId(typeId: String): Either[TypeNotFound, TypeReference] = {
    typeForTypeId(typeId) match {
      case Left(notFound) => Left(notFound)
      case Right(t) => resolveType(t._1)
    }
  }

  def resolveType(tpe: Type): Either[TypeNotFound, TypeReference] = {
    val st = tpe.simpleType
    if (st.baseTypeIsSet) {
      Right(resolveBaseType(st.baseTypeOrThrow))
    } else if (st.containerTypeIsSet) {
      resolveContainerType(st.containerTypeOrThrow)
    } else if (st.typerefIsSet) {
      resolveTyperef(st.typerefOrThrow)
    } else {
      throw new IllegalStateException("Invalid SimpleType")
    }
  }

  def resolveBaseType(baseType: BaseType): TypeReference = {
    baseType.simpleBaseType match {
      case SimpleBaseType.BOOL => BoolRef
      case SimpleBaseType.BYTE => ByteRef
      case SimpleBaseType.I16 => I16Ref
      case SimpleBaseType.I32 => I32Ref
      case SimpleBaseType.I64 => I64Ref
      case SimpleBaseType.DOUBLE => DoubleRef
      case SimpleBaseType.STRING => StringRef
      case SimpleBaseType.BINARY => BinaryRef
      case SimpleBaseType.UnknownWireValue(id) => throw new IllegalStateException("Invalid SimpleBaseType")
    }
  }

  def resolveContainerType(containerType: ContainerType): Either[TypeNotFound, TypeReference] = {
    val sct = containerType.simpleContainerType
    if (sct.listTypeIsSet) {
      val innerTypeId = sct.listTypeOrThrow.elementTypeId
      for (tr <- resolveTypeId(innerTypeId, Annotations.empty).right) yield ListRef(tr)
    } else if (sct.setTypeIsSet) {
      val innerTypeId = sct.setTypeOrThrow.elementTypeId
      for (tr <- resolveTypeId(innerTypeId, Annotations.empty).right) yield SetRef(tr)
    } else if (sct.mapTypeIsSet) {
      val mt = sct.mapTypeOrThrow
      val keyTypeId = mt.keyTypeId
      val valueTypeId = mt.valueTypeId
      for {
        ktr <- resolveTypeId(keyTypeId, Annotations.empty).right
        vtr <- resolveTypeId(valueTypeId, Annotations.empty).right
      } yield MapRef(ktr, vtr)
    } else {
      throw new IllegalStateException("Invalid SimpleContainerType")
    }
  }

  def resolveTyperef(typeref: Typeref): Either[TypeNotFound, TypeReference] = {
    resolveTypeAlias(typeref.typeAlias)
  }

  def resolveTypeAlias(name: String): Either[TypeNotFound, TypeReference] = {
    val option = scope.get(name).map({
      case EnumDecl(name, _) => EnumRef(name)
      case StructDecl(name, _) => StructRef(name)
      case UnionDecl(name, _) => UnionRef(name)
      case ExceptionDecl(name, _) => ExceptionRef(name)
      case ServiceDecl(name, _) => ServiceRef(name)
      case TypedefDecl(name, false, ref, _) => TypedefRef(name, ref)
      case TypedefDecl(name, true, ref, _) => NewtypeRef(name, ref)
    })
    optionToEither(option, AliasNotFound(name))
  }

  def annotationsForType(tpe: Type): Annotations = {
    val st = tpe.simpleType
    if (st.baseTypeIsSet) {
      makeAnnotations(st.baseTypeOrThrow.__annotations)
    } else if (st.containerTypeIsSet) {
      makeAnnotations(st.containerTypeOrThrow.__annotations)
    } else if (st.typerefIsSet) {
      scope.get(st.typerefOrThrow.typeAlias).map(_.annotations).getOrElse(Annotations.empty)
    } else {
      throw new java.lang.Exception("Invalid SimpleType")
    }
  }

  def makeAnnotations(annotations: Seq[Annotation]): Annotations = {
    new Annotations(annotations.map(a => (a.key, a.value)))
  }

  def optionToEither[A, B](right: Option[B], left: A): Either[A, B] = Either.cond(right.isDefined, right.get, left)

}
