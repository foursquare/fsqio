// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.codegen.runtime

/**
 * Utilities for dealing with scopes in Thrift.
 *
 * From package.scala:
 *   type Scope = Map[String, TypeDeclaration]
 */
object Scope {
  /**
   * 
   */
  def scopeFor(program: ProgramSource, programScopes: Map[ProgramSource, Scope]): Scope = {
    val unprefixedMainScope = programScopes.getOrElse(program, Map.empty)
    val mainScope = prefixScope(unprefixedMainScope, "", scalaPrefix(program))
    val prefixedScopes =
      for {
        (name, file) <- (program.includedNames zip program.includedFiles)
        program <- programScopes.keySet.find(_.file == file)
        unprefixedScope <- programScopes.get(program)
      } yield prefixScope(unprefixedScope, name + ".", scalaPrefix(program))

    prefixedScopes.foldLeft(mainScope)(_ ++ _)
  }

  def prefixScope(unprefixedScope: Scope, thriftPrefix: String, scalaPrefix: String): Scope = {
    unprefixedScope.map({ case (key, value) =>
      val prefixedKey = thriftPrefix + key
      val prefixedValue = TypeDeclaration.transform(scalaPrefix + _)(value)
      (prefixedKey, prefixedValue)
    })
  }

  def resolveNamespace(program:ProgramSource): Option[String] = {
    val s = program.tree.namespaces.find(_.language == "scala")
    val j = program.tree.namespaces.find(_.language == "java")
    (s orElse j).map(_.name)
  }

  def scalaPrefix(program: ProgramSource): String = {
    resolveNamespace(program).map(_ + ".").getOrElse("")
  }
}
