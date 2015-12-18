// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.codegen.runtime

import io.fsq.spindle.__shaded_for_spindle_bootstrap__.descriptors.{Annotation, Typedef}
import io.fsq.spindle.__shaded_for_spindle_bootstrap__.runtime.Annotations
import scala.annotation.tailrec

/**
 * TypeDeclarations are used to bridge the gap between the type information
 * contained in the TypeRegistry (which can only resolve types included in the
 * same file) and the type information needed to compile a file (which might
 * span several source files, because of includes).
 *
 * TypeDeclarationResolver will look across several source files and resolve
 * all the types declared in any of those files.
 *
 * The following Thrift keywords can declare new types: enum, struct, union,
 * exception, service, typedef.
 */
class TypeDeclarationResolver(enhancedTypes: EnhancedTypes) {

  /**
   * Takes a set of programs and resolves all its type declarations.
   *
   * This is accomplished in a series of passes. The first pass resolves all
   * type declarations which cannot reference other types (enum, struct, union
   * exception). Subsequent passes attempt to resolve type declarations that
   * can reference other types (typedefs).
   *
   * @param programs - set of programs for which to resolve type declarations
   */
  def resolveAllTypeDeclarations(programs: Seq[ProgramSource]): Map[ProgramSource, Map[String, TypeDeclaration]] = {
    val zerothPass = Map.empty[ProgramSource, Map[String, TypeDeclaration]]
    val firstPass = programs.map(firstPassResolveTypeDeclarations).foldLeft(zerothPass)(mergeMaps)
    val nthPass = recursiveResolveTypeDeclarations(programs, firstPass)
    nthPass
  }

  /**
   * Takes a program and resolves the "first pass" TypeDeclarations. That is,
   * the easy ones.
   *
   * Resolves types declared as enums, structs, unions, and exceptions.
   * Typedefs are left for the recursive phase, because they can reference
   * types across source boundaries.
   *
   * Types declared as services are not resolved, because they cannot be
   * referenced in Thrift files.
   *
   * @param program - program for which to resolve type declarations
   */
  def firstPassResolveTypeDeclarations(program: ProgramSource): Map[ProgramSource, Map[String, TypeDeclaration]] = {
    val enumDecls = program.tree.enums.map(e => EnumDecl(e.name, makeAnnotations(e.__annotations)))
    val structDecls = program.tree.structs.map(s => StructDecl(s.name, makeAnnotations(s.__annotations)))
    val unionDecls = program.tree.unions.map(u => UnionDecl(u.name, makeAnnotations(u.__annotations)))
    val exceptionDecls = program.tree.exceptions.map(e => ExceptionDecl(e.name, makeAnnotations(e.__annotations)))
    val serviceDecls = program.tree.services.map(s => ServiceDecl(s.name, makeAnnotations(s.__annotations)))

    // TODO: throw on duplicate names
    val decls: Seq[TypeDeclaration] = enumDecls ++ structDecls ++ unionDecls ++ exceptionDecls ++ serviceDecls
    val declsMap: Map[String, TypeDeclaration] = seqToMapByKey(decls)(_.name)
    Map(program -> declsMap)
  }

  /**
   * Takes a set of programs and recursively resolves all the types declared as
   * typedefs.
   *
   * These declarations can reference types across source boundaries, so we
   * must keep track of all included program sources and all the type
   * declarations seen so far in order to successfully resolve.
   *
   * For each pass, the set of type declarations which have not yet been
   * resolved is found. If the set is empty, the recursion is done. If the set
   * is non-empty, an attempt is made to resolve those type declarations. If
   * any progress is made, the recursion continues. If no progress is made, the
   * recursion terminates with an exception.
   *
   * @param programs - set of programs for which to resolve typedef declarations
   * @param decls - a map of already-resolved type declarations, keyed first by
   *                program name and then by type alias
   */
  @tailrec
  final def recursiveResolveTypeDeclarations(
      programs: Seq[ProgramSource],
      decls: Map[ProgramSource, Map[String, TypeDeclaration]]
  ): Map[ProgramSource, Map[String, TypeDeclaration]] = {
    val unresolvedTypedefs = findUnresolvedTypedefs(programs, decls)
    if (unresolvedTypedefs.isEmpty) {
      // Base case: nothing left to resolve, we're done.
      decls
    } else {
      val additionalDecls = unresolvedTypedefs.groupBy(_.program).map({ case (program, scopedTypedef) =>
        (program, resolveAdditional(scopedTypedef))
      })

      if (additionalDecls.values.map(_.size).sum == 0) {
        // Base case: no progress was made, abort.
        throw new CodegenException(unresolvedTypedefs.map(debugScopedTypedef).mkString("\n"))
      } else {
        // Recursive case: continue resolving.
        val mergedDecls = mergeMaps(decls, additionalDecls)
        recursiveResolveTypeDeclarations(programs, mergedDecls)
      }
    }
  }

  /**
   * Helper class to hold a type declaration and all the parts necessary for it
   * to be properly scoped.
   *
   * @param typedef - a type declaration
   * @param programName - the name of the program it appears in
   * @param scope - the scope as seen from the program it appears in
   */
  case class ScopedTypedef(typedef: Typedef, program: ProgramSource, scope: Map[String, TypeDeclaration])

  /**
   * Given a set of programs and the currently resolved type declarations,
   * finds any typedefs that are yet to be resolved and properly scopes them.
   *
   * @param programs - set of programs for which to resolve typedef declarations
   * @param decls - a map of already-resolved type declarations, keyed first by
   *                program name and then by type alias
   */
  def findUnresolvedTypedefs(
      programs: Seq[ProgramSource],
      decls: Map[ProgramSource, Map[String, TypeDeclaration]]
  ): Seq[ScopedTypedef] = {
    for {
      program <- programs
      scope = Scope.scopeFor(program, decls)
      typedef <- program.tree.typedefs
      if !scope.contains(typedef.typeAlias)
    } yield ScopedTypedef(typedef, program, scope)
  }

  /**
   * Given a set of scoped type declarations, attempts to resolve them. Returns
   * a map of successfully resolved type declarations.
   *
   * May not resolve all the given type declarations.
   *
   * @param scopedTypedefs - set of scoped type declarations to resolve
   */
  def resolveAdditional(scopedTypedefs: Seq[ScopedTypedef]): Map[String, TypeDeclaration] = {
    (for {
      scopedTypedef <- scopedTypedefs
      typeId = scopedTypedef.typedef.typeId
      annotations = makeAnnotations(scopedTypedef.typedef.__annotations)
      newType = annotations.get("new_type").exists(_ == "true")
      program = scopedTypedef.program
      registry = program.tree.typeRegistry
      scope = scopedTypedef.scope
      resolver = new TypeReferenceResolver(registry, scope, enhancedTypes)
      alias = scopedTypedef.typedef.typeAlias
      name = program.capitalizedBaseName + "Typedefs." + alias
      typeref <- resolver.resolveTypeId(typeId, annotations).right.toOption
    } yield (alias -> TypedefDecl(name, newType, typeref, annotations))).toMap
  }

  def seqToMapBy[T, K, V](xs: Seq[T])(f: T => (K, V)): Map[K, V] = {
    val builder = Map.newBuilder[K, V]
    builder.sizeHint(xs.size)

    xs.foreach(x => {
      builder += f(x)
    })

    builder.result
  }

  def seqToMapByKey[T, K](xs: Seq[T])(f: T => K): Map[K, T] = {
    seqToMapBy(xs)(t => (f(t), t))
  }

  /**
   * Helper method to merge two nested maps.
   */
  def mergeMaps[A, B, C](x: Map[A, Map[B, C]], y: Map[A, Map[B, C]]): Map[A, Map[B, C]] = {
    val keys = (x.keySet ++ y.keySet).toSeq
    seqToMapBy(keys)(key => {
      val xs = x.getOrElse(key, Map.empty)
      val ys = y.getOrElse(key, Map.empty)
      (key, xs ++ ys)
    })
  }

  def debugScopedTypedef(unresolvedTypedef: ScopedTypedef): String = {
    val id = unresolvedTypedef.typedef.typeId
    val tpe = unresolvedTypedef.program.tree.typeRegistry.idToType()(id)
    val alias = tpe.simpleType.typerefOrThrow.typeAlias
    "Unresolvable typedef refers to non-existent type " + alias + " in " + unresolvedTypedef.program.file.toString
  }

  def makeAnnotations(annotations: Seq[Annotation]): Annotations = {
    new Annotations(annotations.map(a => (a.key, a.value)))
  }
}
