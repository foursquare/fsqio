// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.codegen.parser

import io.fsq.spindle.__shaded_for_spindle_bootstrap__.descriptors.{Enum, Field, Function, Program, Service}
import java.io.File

class ThriftValidator(file: File) {
  def validateProgram(program: Program): Unit = {
    program.enums.foreach(validateEnum)
    program.structs.foreach(struct => validateFields(struct.__fields, "Struct " + struct.name))
    program.unions.foreach(union => validateFields(union.__fields, "Union " + union.name))
    program.exceptions.foreach(exception => validateFields(exception.__fields, "Exception " + exception.name))
    program.services.foreach(validateService)
    validateDistinct(program.constants.map(_.name), "constant name(s)")

    val names = (program.constants.map(_.name) ++ program.enums.map(_.name) ++ program.typedefs.map(_.typeAlias) ++
      program.structs.map(_.name) ++ program.unions.map(_.name) ++ program.exceptions.map(_.name) ++
      program.services.map(_.name))
    validateDistinct(names, "type or term name(s)")
  }

  def validateEnum(enum: Enum): Unit = {
    val where = "Enum " + enum.name
    validateDistinct(enum.elements.map(_.name), "enum name(s)", Some(where))
    validateDistinct(enum.elements.map(_.value), "enum value(s)", Some(where))
  }

  def validateService(service: Service): Unit = {
    val where = "Service " + service.name
    validateDistinct(service.functions.map(_.name), "function name(s)", Some(where))
    service.functions.foreach(function => validateFunction(function,  where))
  }

  def validateFunction(function: Function, _where: String): Unit = {
    val where = "Function %s in %s".format(function.name, _where)
    validateFields(function.argz, where)
    validateFields(function.throwz, where)
  }

  def validateFields(fields: Seq[Field], where: String): Unit = {
    validateDistinct(fields.map(_.identifier), "field identifier(s)", Some(where))
    validateDistinct(fields.map(_.name), "field name(s)", Some(where))
  }

  def validateDistinct[A](xs: Seq[A], what: String, where: Option[String] = None): Unit = {
    if (xs.size != xs.distinct.size) {
      val repeats = xs.diff(xs.distinct)
      val message =
        where match {
          case Some(w) =>
            "Repeated %s in %s: %s".format(what, w, repeats.mkString(", "))
          case None =>
            "Repeated %s: %s".format(what, repeats.mkString(", "))
        }
      throw new ParserException(message, file)
    }
  }
}
