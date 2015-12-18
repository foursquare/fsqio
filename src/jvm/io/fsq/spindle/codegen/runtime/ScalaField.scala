// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.codegen.runtime

import io.fsq.spindle.__shaded_for_spindle_bootstrap__.descriptors.{Field, FieldProxy, Requiredness}
import scala.annotation.tailrec

class ScalaField(
    override val underlying: Field,
    resolver: TypeReferenceResolver,
    val isPrimaryKey: Boolean = false,
    val isForeignKey: Boolean = false
) extends FieldProxy with HasAnnotations {
  val escapedName: String = CodegenUtil.escapeScalaFieldName(name)
  val wireNameOpt: Option[String] = annotations.get("wire_name")
  val wireName: String = wireNameOpt.getOrElse(name)
  val varName: String = "_" + name
  val tfieldName: String = name.toUpperCase + "_FDESC"
  val isSetName: String = name + "IsSet"
  val isSetVar: String = "_" + isSetName
  val builderRequired: Boolean = 
    (this.requirednessIsSet == false
      || this.requirednessOption.exists(_ == Requiredness.REQUIRED)
      || isPrimaryKey
      || annotations.get("builder_required").exists(_ == "true"))
  val typeReference: TypeReference =
    resolver.resolveTypeId(underlying.typeId, annotations).fold(
      missingTypeReference => missingTypeReference match {
        case AliasNotFound(typeAlias) =>
          throw new CodegenException("Unknown type `%s` referenced in field %d: _ %s".format(
            typeAlias, underlying.identifier, underlying.name))
        case TypeIdNotFound(typeId) =>
          throw new CodegenException("Unresolveable type id `%s` in field %d: _ %s".format(
            typeId, underlying.identifier, underlying.name))
        case EnhancedTypeFailure =>
          throw new CodegenException("Failure with enhanced types in field %d: _ %s".format(
            underlying.identifier, underlying.name))
      },
      foundTypeReference => foundTypeReference
    )

  // TODO: implement. must be in terms of custom TypeReference
  val enhancedTypeAnnotations = extractEnhancedType(typeReference)
  val renderType: RenderType = RenderType(typeReference, annotations)

  val nullOrDefault = if (this.defaultValueIsSet || !renderType.isNullable) "OrDefault" else "OrNull"
  val defaultName = this.name + nullOrDefault

  /**
   * Should this field be serialized with an enhanced type?
   *
   * This is used by TBSONObjectProtocol (and possibly other custom protocols)
   * to know whether a given field should get special treatment when being
   * serialized.
   */
  @tailrec
  final def extractEnhancedType(tpe: TypeReference): Option[(String, String)] = tpe match {
    case EnhancedTypeRef(name, _) =>
      val parts = name.split(':')
      Some(parts(0), parts(1))
    // We unroll typedefs to determine the underlying serialization
    case TypedefRef(_, ref) =>
      extractEnhancedType(ref)
    case NewtypeRef(_, ref) =>
      extractEnhancedType(ref)
    // NOTE: these are ugly but necessary hacks. The only hook we have
    // to pass on info about enhanced types is the writeFieldBegin call to
    // TProtocol, so if there is an enhanced type anywhere inside, we must pass
    // it in at the nearest field. For collections, this means now.
    case ListRef(ref) =>
      extractEnhancedType(ref)
    case SetRef(ref) =>
      extractEnhancedType(ref)
    case MapRef(_, ref) =>
      extractEnhancedType(ref)
    case _ =>
      None
  }
}
