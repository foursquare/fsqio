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
    var attemptToSupport = false
    val elementsMissingAlternativeValue = new scala.collection.mutable.HashSet[String]
    val existingValues = new scala.collection.mutable.HashSet[String]

    for (element <- elements) {
      element.alternateValue match {
        case Some(alternateValue) =>
          attemptToSupport = true
          if (existingValues.contains(alternateValue)) {
            throw new CodegenException("Duplicate string_value's detected. They must be unique.")
          }
          existingValues += alternateValue

        case None =>
          elementsMissingAlternativeValue += element.name
      }
    }

    if (attemptToSupport && elementsMissingAlternativeValue.nonEmpty) {
      throw new CodegenException(
        s"Must define alternate values for all enum elements, but " +
          s"missing from ${elementsMissingAlternativeValue.mkString(", ")}"
      )
    }

    elementsMissingAlternativeValue.isEmpty
  }

  // Check that no retired_ids are being used
  {
    // In an extra scope to prevent these vals from being public on the class
    val ids = elements.map(_.value).toSet
    val retiredIds = annotations.getAll("retired_ids").flatMap(_.split(',')).map(_.toInt).toSet

    val badIds = for {
      element <- elements
      if retiredIds.contains(element.value)
    } yield {
      s"${element.value}: ${element.name}"
    }

    if (badIds.nonEmpty) {
      throw new CodegenException(s"Error: illegal use of retired_ids: ${badIds.mkString("; ")}")
    }

    val retiredStringValues = annotations.getAll("retired_wire_names").flatMap(_.split(',')).toSet
    val badStringValues = for {
      element <- elements
      stringValue = element.alternateValue.getOrElse(element.name)
      if retiredStringValues.contains(stringValue)
    } yield {
      element.alternateValue match {
        case None => s"${element.value}: ${element.name}}"
        case Some(alternateValue) => s"${element.value}: ${element.name} string_value=${alternateValue}}"
      }
    }

    if (badStringValues.nonEmpty) {
      throw new CodegenException(s"Error: illegal use of retired string_vale: ${badStringValues.mkString("; ")}")
    }
  }
}
