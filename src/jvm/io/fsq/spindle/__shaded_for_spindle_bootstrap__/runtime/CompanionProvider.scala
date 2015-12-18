// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.__shaded_for_spindle_bootstrap__.runtime

trait CompanionProvider[T] {
  type CompanionT
  def provide: CompanionT
}

object CompanionProvider {
  def apply[T](implicit c: CompanionProvider[T]): c.CompanionT = c.provide
}
