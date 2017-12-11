// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.__shaded_for_spindle_bootstrap__.runtime

class Annotations(val toSeq: Seq[(String, String)]) {
  def contains(key: String): Boolean = toSeq.exists(_._1 == key)

  def getAll(key: String): Seq[String] = toSeq.filter(_._1 == key).map(_._2)

  def get(key: String): Option[String] = {
    val annotations = getAll(key)
    if (annotations.size > 1) {
      throw new IllegalStateException("More than one annotation value for found for key: " + key)
    }
    annotations.headOption
  }

  def isEmpty: Boolean = toSeq.isEmpty

  def nonEmpty: Boolean = toSeq.nonEmpty

  def ++(that: Annotations): Annotations = {
    new Annotations(this.toSeq ++ that.toSeq)
  }
}

object Annotations {
  val empty = new Annotations(Seq.empty)
}
