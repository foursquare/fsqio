// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.codegen.runtime

import io.fsq.spindle.__shaded_for_spindle_bootstrap__.descriptors.Annotation
import io.fsq.spindle.__shaded_for_spindle_bootstrap__.runtime.Annotations

trait HasAnnotations {
  def __annotations: Seq[Annotation]

  val annotations: Annotations = makeAnnotations(__annotations)

  private def makeAnnotations(annotations: Seq[Annotation]): Annotations = {
    new Annotations(annotations.map(a => (a.key, a.value)))
  }
}
