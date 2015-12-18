// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.codegen.runtime

import io.fsq.spindle.__shaded_for_spindle_bootstrap__.runtime.Annotations

object `package` {
  type Scope = Map[String, TypeDeclaration]
  type EnhancedTypes = (TypeReference, Annotations, Scope) => Option[TypeReference]
}
