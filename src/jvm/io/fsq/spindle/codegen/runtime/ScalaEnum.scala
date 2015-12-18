// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.codegen.runtime

import io.fsq.spindle.__shaded_for_spindle_bootstrap__.descriptors.{Enum, EnumElement, EnumElementProxy, EnumProxy}

class ScalaEnumElement(override val underlying: EnumElement) extends EnumElementProxy with HasAnnotations {
  val alternateValue: Option[String] = {
    val stringValueAnnotations = __annotations.find(_.keyOption.exists(_ == "string_value"))
    if (stringValueAnnotations.size > 1) {
      throw new Exception("Multiple string_value annotations on annotation")
    }
    stringValueAnnotations.headOption.flatMap(_.valueOption)
  }
}

class ScalaEnum(override val underlying: Enum) extends EnumProxy with HasAnnotations {
  override val elements: Seq[ScalaEnumElement] = underlying.elements.map(elem => new ScalaEnumElement(elem))

  val supportsAlternateValues: Boolean = {
    var supported = true
    var attemptToSupport = false
    val existingValues = new scala.collection.mutable.HashSet[String]

    for (element <- elements) {
      element.alternateValue match {
        case Some(alternateValue) =>
          attemptToSupport = true
          if (existingValues.contains(alternateValue)) {
            throw new Exception("Duplicate string_value's detected. They must be unique.")
          }
          existingValues += alternateValue

        case None =>
          supported = false
      }
    }

    if (attemptToSupport && !supported) {
      throw new Exception("Must define alternate values for all enum elements")
    }

    supported
  }
}

