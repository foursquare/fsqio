// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.enhanced

import com.google.common.geometry.{S2Cell, S2CellId, S2LatLng}
import io.fsq.rogue.LatLong

// TODO: This is duplicated with GeoS2Field, unify them.
class LatLngHacc(val value: Long) {
  val ll: LatLong = LatLngHacc.toLatLong(value)
  val haccOpt: Option[Int] = LatLngHacc.toHacc(value)
  val cellId: S2CellId = LatLngHacc.toCellId(value)
}

object LatLngHacc {
  val HaccMask: Long = 0x0000000000000FFF
  val Level = 24

  def toCellId(v: Long): S2CellId = new S2CellId(v & ~HaccMask)

  def toLatLong(v: Long): LatLong = {
    val center = new S2LatLng(new S2Cell(toCellId(v).parent(Level)).getCenter)
    LatLong(center.latDegrees, center.lngDegrees)
  }

  def toHacc(v: Long): Option[Int] = {
    Some((v & HaccMask).toInt).filterNot(_ == 0)
  }
}
