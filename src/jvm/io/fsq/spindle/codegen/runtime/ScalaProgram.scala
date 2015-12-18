// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.codegen.runtime

import io.fsq.spindle.__shaded_for_spindle_bootstrap__.descriptors.{Program, ProgramProxy}

class ScalaProgram(
    override val underlying: Program,
    val scope: Map[String, TypeDeclaration],
    val enhancedTypes: EnhancedTypes
) extends ProgramProxy {
  def pkg: Option[String] = {
    val sc = underlying.namespaces.find(_.language == "scala")
    val ja = underlying.namespaces.find(_.language == "java")
    (sc orElse ja).map(_.name)
  }

  def jsPackage: Option[String] = underlying.namespaces.find(_.language == "js").map(_.name)

  val resolver = new TypeReferenceResolver(typeRegistry, scope, enhancedTypes)

  override val enums: Seq[ScalaEnum] = underlying.enums.map(new ScalaEnum(_))
  override val typedefs: Seq[ScalaTypedef] = underlying.typedefs.map(new ScalaTypedef(_, resolver))
  override val structs: Seq[ScalaClass] = underlying.structs.map(new ScalaClass(_, resolver))
  override val unions: Seq[ScalaUnion] = underlying.unions.map(new ScalaUnion(_, resolver))
  override val exceptions: Seq[ScalaException] = underlying.exceptions.map(new ScalaException(_, resolver))
  override val services: Seq[ScalaService] = underlying.services.map(new ScalaService(_, resolver))
  override val constants: Seq[ScalaConst] = underlying.constants.map(new ScalaConst(_, resolver))
}

object ScalaProgram {
  def apply(
      program: ProgramSource,
      typeDeclarations: Map[ProgramSource, Map[String, TypeDeclaration]],
      enhancedTypes: EnhancedTypes
  ): ScalaProgram = {
    new ScalaProgram(program.tree, Scope.scopeFor(program, typeDeclarations), enhancedTypes)
  }
}
