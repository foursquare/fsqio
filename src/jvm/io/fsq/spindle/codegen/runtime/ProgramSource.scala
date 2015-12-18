// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.codegen.runtime

import io.fsq.spindle.__shaded_for_spindle_bootstrap__.descriptors.Program
import java.io.File
import org.apache.commons.io.FilenameUtils

/**
 * This class represents a Thrift source file.
 *
 * It can provide the path to a file, the AST of the program in the file,
 * the name of the file, and the names of the included files.
 */
case class ProgramSource(file: File, tree: Program, includedFiles: Seq[File]) {
  val name = file.getName
  val baseName = FilenameUtils.removeExtension(name)
  val capitalizedBaseName = baseName.split('_').map(_.capitalize).mkString
  val includedNames = includedFiles.map(file => FilenameUtils.removeExtension(file.getName))

  override final def equals(that: Any): Boolean = that match {
    case ProgramSource(thatFile, _, _) => this.file.getPath == thatFile.getPath
    case _ => false
  }

  override final val hashCode: Int = file.getPath.hashCode
}
