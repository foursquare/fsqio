// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.codegen.runtime

import io.fsq.spindle.__shaded_for_spindle_bootstrap__.descriptors.{Exception, ExceptionProxy}

class ScalaException(
    override val underlying: Exception,
    resolver: TypeReferenceResolver
) extends ExceptionProxy with StructLike {
  override val __fields: Seq[ScalaField] = underlying.__fields.map(new ScalaField(_, resolver))
  override def isException: Boolean = true
}
