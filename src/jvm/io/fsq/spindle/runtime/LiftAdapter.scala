// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.runtime

trait LiftAdapter[Id] {
  def create: AnyRef

  def valueFromWireName(name: String): Option[Any]
  def primedObjFromWireName(name: String): Option[AnyRef]
  def primedObjSeqFromWireName(name: String): Seq[AnyRef]

  def setValueFromWireName(name: String, value: Any): Unit
  def clearValueFromWireName(name: String): Unit
}
