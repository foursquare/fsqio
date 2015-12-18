// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.codegen.runtime

import io.fsq.spindle.__shaded_for_spindle_bootstrap__.descriptors.{Const, ConstProxy}
import io.fsq.spindle.__shaded_for_spindle_bootstrap__.runtime.Annotations



class ScalaConst(override val underlying: Const, resolver: TypeReferenceResolver)
extends ConstProxy {
  val typeReference: TypeReference =
    resolver.resolveTypeId(underlying.typeId).fold( missingTR =>
      missingTR match {
        case AliasNotFound(typeAlias) => {
          throw new CodegenException("Unknown type `%s` referenced in const %s".format(
            typeAlias, underlying.name))
        }
        case TypeIdNotFound(typeId) => {
          throw new CodegenException("Unresolveable type id `%s` in const %s".format(
            typeId, underlying.name))
        }
        case EnhancedTypeFailure => {
          throw new CodegenException("Failure with enhanced types in const %s".format(
            underlying.name))
        }
      }, foundTypeReference => foundTypeReference
    )
  val renderType = RenderType(typeReference, Annotations.empty)
  override def valueOption: Option[String] = renderType.renderValue(underlying.value)
  def jsValueOption: Option[String] = renderType match {
    // this is a horrible, HORRIBLE hack. We need to properly extract the js rendering
    case r: PrimitiveRenderType => Some(underlying.value)
    case StringRenderType => Some(underlying.value)
    case _ => None
  }

}
