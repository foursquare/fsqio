// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.codegen.runtime

import io.fsq.spindle.__shaded_for_spindle_bootstrap__.descriptors.{Struct, StructProxy}
import io.fsq.spindle.__shaded_for_spindle_bootstrap__.runtime.{IndexParser, InvalidField, InvalidIndex}

class ScalaClass(
    override val underlying: Struct,
    resolver: TypeReferenceResolver
) extends StructProxy with StructLike {
  private val primaryKeyFieldName: Option[String] = {
    annotations.get("primary_key") orElse {
      if (annotations.contains("mongo_collection")) {
        throw new CodegenException("Error: primary_key annotation must be specified on mongo record %s".format(this.name))
      }
      None
    }
  }
  private val foreignKeyFieldNames: Seq[String] = annotations.getAll("foreign_key")

  private val existingFieldNames: Set[String] = underlying.__fields.map(_.name).toSet
  for (pkField <- primaryKeyFieldName) {
    if (!existingFieldNames.contains(pkField)) {
      throw new CodegenException("Error: primary_key annotation references non-existing field %s".format(pkField))
    }
  }

  private val missingForeignKeyFieldNames: Set[String] = foreignKeyFieldNames.toSet -- existingFieldNames
  if (missingForeignKeyFieldNames.nonEmpty) {
    throw new CodegenException("Error: foreign_key annotations reference non-existing fields: %s".format(
      missingForeignKeyFieldNames.mkString(", ")))
  }

  override val __fields: Seq[ScalaField] =
    for (field <- underlying.__fields) yield {
      val isPrimaryKey = primaryKeyFieldName.exists(_ == field.name)
      val isForeignKey = foreignKeyFieldNames.exists(_ == field.name)
      new ScalaField(field, resolver, isPrimaryKey, isForeignKey)
    }

  override val generateProxy = annotations.contains("generate_proxy")
  override val generateLiftAdapter = annotations.contains("generate_lift_adapter")

  // Check that no retired_ids or retired_wire_names are being used in
  {
    // In an extra scope to prevent these vals from being public on the class
    val ids = __fields.map(_.identifier).toSet
    val wireNames = __fields.map(_.wireName).toSet
    val retiredIds = annotations.getAll("retired_ids").flatMap(_.split(',')).map(_.toShort).toSet
    val retiredWireNames = annotations.getAll("retired_wire_names").flatMap(_.split(',')).toSet
    val repeatedWireNames = __fields.groupBy(_.wireName).filter(_._2.size > 1).keys.toSeq
    val badIds = ids.intersect(retiredIds)
    val badWireNames = wireNames.intersect(retiredWireNames)
    if (repeatedWireNames.nonEmpty) {
      throw new CodegenException("Error: illegal repetition of wire_name's: %s".format(repeatedWireNames.mkString(", ")))
    }
    if (badIds.nonEmpty) {
      throw new CodegenException("Error: illegal use of retired ids: %s".format(badIds.mkString(", ")))
    }
    if (badWireNames.nonEmpty) {
      throw new CodegenException("Error: illegal use of retired wire names: %s".format(badWireNames.mkString(", ")))
    }
  }

  // Check that index annotations parse and indexed fields actually exist
  {
    IndexParser.parse(this.annotations) match {
      case Left(errs) => errs.foreach {
        case InvalidField(fieldSpecifier) =>
          throw new CodegenException(
            "Invalid index specifier '%s' for class %s -- must be FIELD_NAME:INDEX_TYPE".format(
              fieldSpecifier, this.name))
        case InvalidIndex(indexSpecifier) =>
          throw new CodegenException(
            "Unknown index type specifier '%s' for class %s".format(indexSpecifier, this.name))
      }
      case Right(indexes) =>
        for {
          index <- indexes
          indexEntry <- index
        } {
          val fieldNames = indexEntry.fieldName.split('.')
          if (fieldNames.size < 1) {
            throw new CodegenException(
              "Unknown field name '' in index specifier for class %s".format(this.name))
          }

          // TODO: verify subfields exist
          val fieldName = fieldNames.head
          if (!this.fields.exists(field => field.name == fieldName)) {
            throw new CodegenException(
              "Unknown field name '%s' in index specifier for class %s".format(fieldName, this.name))
          }
        }
    }
  }

  override val primaryKeyField: Option[ScalaField] = __fields.find(_.isPrimaryKey)
}
