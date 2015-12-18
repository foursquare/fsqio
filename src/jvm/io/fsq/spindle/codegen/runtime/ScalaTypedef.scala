// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.codegen.runtime

import io.fsq.spindle.__shaded_for_spindle_bootstrap__.descriptors.{Typedef, TypedefProxy}

class ScalaTypedef(
    override val underlying: Typedef,
    resolver: TypeReferenceResolver
) extends TypedefProxy with HasAnnotations {
  val typeReference: TypeReference =
    resolver.resolveTypeId(underlying.typeId, annotations).fold(
      missingTypeReference => missingTypeReference match {
        case AliasNotFound(typeAlias) =>
          throw new CodegenException("Unknown type `%s` referenced in typedef %s _".format(
            typeAlias, underlying.typeAlias))
        case TypeIdNotFound(typeId) =>
          throw new CodegenException("Unresolveable type id `%s` in typedef %s _".format(
            typeId, underlying.typeAlias))
        case EnhancedTypeFailure =>
          throw new CodegenException("Failure with enhanced types in typedef %s _".format(
            underlying.typeAlias))
      },
      foundTypeReference => foundTypeReference
    )

  val renderType = RenderType(typeReference, annotations)

  val newType = annotations.get("new_type").exists(_ == "true")
}
