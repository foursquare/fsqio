// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.dollar
/*
 * An implementation of DollarAmount with only cent-level precision.
 *
 * Intended to be used where only cent-level precision is allowed (eg charging cards)
 * and any rounding business logic should have already been invoked.
 * Note most operations will yield a DollarAmount and require re-rounding.
 */
class CentAmount(val cents: Long) extends DollarAmount(cents * DollarAmount.centShift) {
  override def asCents(round: DollarAmount.Rounding): Long = cents
  def plusCents(other: CentAmount): CentAmount = new CentAmount(cents + other.cents)
  def minusCents(other: CentAmount): CentAmount = new CentAmount(cents - other.cents)
}
