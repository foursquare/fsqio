// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.codegen.runtime

trait StructLike extends HasAnnotations {
  def __fields: Seq[ScalaField]

  def name: String
  def fields: Seq[ScalaField] = __fields

  val tstructName = name.toUpperCase + "_SDESC"
  def primaryKeyField: Option[ScalaField] = None
  def isException: Boolean = false
  def generateProxy: Boolean = false
  def generateLiftAdapter: Boolean = false
  def generateMutable: Boolean = false
}
