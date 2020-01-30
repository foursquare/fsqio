// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.dollar

import java.text.NumberFormat
import java.util.Locale
import scala.math.BigDecimal

class DollarAmount protected (val value: Long) extends Ordered[DollarAmount] {
  def asMicros: Long = value
  def asCents(round: DollarAmount.Rounding): Long = DollarAmount.div(value, DollarAmount.centShift, round)
  def asDollars(round: DollarAmount.Rounding): Long = DollarAmount.div(value, DollarAmount.dollarShift, round)

  def roundedToCents(round: DollarAmount.Rounding): CentAmount = DollarAmount.fromCents(asCents(round))
  def roundedHalfEvenToCents: CentAmount = DollarAmount.fromCents(asCents(DollarAmount.RoundHalfToEven))

  override def toString: String = DollarAmount.format(this)

  def toString(
    forceCents: Boolean,
    commaify: Boolean = false,
    withDollarSign: Boolean = true,
    locale: Locale = Locale.US
  ): String = {
    DollarAmount.format(this, forceCents, commaify, withDollarSign, locale)
  }

  override def equals(other: Any) = other match {
    case d: DollarAmount => value == d.value
    case _ => false
  }

  // Implementation of Ordered.
  override def compare(other: DollarAmount) = this.value.compare(other.value)

  override def hashCode: Int = value.toInt

  def isNegative: Boolean = value < 0
  def isPositive: Boolean = value >= 0
  def abs: DollarAmount = if (isPositive) this else new DollarAmount(-value)

  def unary_- = new DollarAmount(-value)

  def +(other: DollarAmount): DollarAmount = new DollarAmount(value + other.value)
  def -(other: DollarAmount): DollarAmount = new DollarAmount(value - other.value)
  def /(other: DollarAmount): Double = 1.0 * value / other.value
  def %(other: DollarAmount): Long = value % other.value

  // Be careful with truncating division (this is why we have 4 decimal places to spare)
  def /(divisor: Int): DollarAmount = new DollarAmount(value / divisor)
  def /(divisor: Long): DollarAmount = new DollarAmount(value / divisor)
  def /(divisor: Double): DollarAmount = new DollarAmount((value / divisor).toLong)
  def *(multiplicand: Int): DollarAmount = new DollarAmount(value * multiplicand)
  def *(multiplicand: Long): DollarAmount = new DollarAmount(value * multiplicand)
  def *(multiplicand: Double): DollarAmount = new DollarAmount((value * multiplicand).toLong)

  def max(other: DollarAmount): DollarAmount = {
    if (this < other) other else this
  }

  def min(other: DollarAmount): DollarAmount = {
    if (this < other) this else other
  }
}

object DollarAmount {
  val centShift: Long = 10000
  private val dollarShift: Long = centShift * 100
  val zero = DollarAmount.fromDollars(0)
  val one = DollarAmount.fromDollars(1)
  val halfCent = DollarAmount.fromMicros(centShift / 2)

  def fromCents(cents: Long): CentAmount = new CentAmount(cents)
  def fromDollars(dollars: Long): CentAmount = fromCents(dollars * 100)
  def fromMicros(micros: Long): DollarAmount = new DollarAmount(micros)
  def fromDollars(dollars: String): DollarAmount = {
    val cleanDollars = dollars.replaceAll("[,$]", "")
    DollarAmount.fromMicros((BigDecimal(cleanDollars) * dollarShift).toLong)
  }
  def fromString(dollars: String): DollarAmount = fromDollars(dollars)

  def format(
    amount: DollarAmount,
    forceCents: Boolean = false,
    commaify: Boolean = false,
    withDollarSign: Boolean = true,
    locale: Locale = Locale.US
  ): String = {
    val total: Long = amount.asCents(DollarAmount.RoundDown)
    val totalCents: Long = math.abs(total)
    val dollars: Long = totalCents / 100
    val cents: Long = totalCents % 100
    val dollarsStr: String = {
      if (commaify) {
        NumberFormat.getIntegerInstance(locale).format(dollars)
      } else {
        dollars.toString
      }
    }
    val signStr = if (total < 0) "-" else ""
    val dollarSign = if (withDollarSign) "$" else ""
    if (forceCents || cents > 0) {
      "%s%s%s.%02d".format(signStr, dollarSign, dollarsStr, math.abs(cents))
    } else {
      "%s%s%s".format(signStr, dollarSign, dollarsStr)
    }
  }

  sealed trait Rounding
  case object RoundUp extends Rounding
  case object RoundDown extends Rounding
  case object RoundNearest extends Rounding
  case object RoundHalfToEven extends Rounding

  private def div(numerator: Long, denominator: Long, roundStrategy: Rounding): Long = roundStrategy match {
    case RoundDown => numerator / denominator
    case RoundUp => (numerator + denominator - 1) / denominator
    case RoundNearest => (numerator + (denominator) / 2) / denominator
    case RoundHalfToEven => {
      /*
       * Round using the Round-Half-to-Even method, also known as or unbiased rounding or banker's rounding.
       * The sum of values rounded with this method has the same expected value as the sum of the values
       * themselves (thus being an 'unbiased' rounding method).
       *
       * See: http://en.wikipedia.org/wiki/Rounding#Round_half_to_even
       *
       * Summary:
       *  - Round to the nearest whole value when there is one (eg remainder is not exactly 0.5).
       *  - When remainder is exactly 0.5, round towards the nearest even whole value
       */
      val roundedDown = numerator / denominator
      val roundedUp = roundedDown + math.signum(numerator)

      val half = denominator / 2
      val remainder = math.abs(numerator % denominator) // `math.abs(numerator - roundedDown * denominator)` is faster

      if (remainder < half) {
        roundedDown
      } else if (remainder > half) {
        roundedUp
      } else { // (remainder == half)
        if ((roundedDown & 1) == 0) { // roundedDown is even.
          roundedDown
        } else { // roundedDown is not even, so roundedUp must be
          roundedUp
        }
      }
    }
  }

  def sum(values: Seq[DollarAmount]): DollarAmount = {
    DollarAmount.fromMicros(values.map(_.asMicros).sum)
  }

  trait Implicits {
    // So we can write stuff like 500.microUSD or 5.USD.
    implicit def long2DollarAmountWrapper(value: Long) = new {
      def microUSD = DollarAmount.fromMicros(value)
      def centsUSD = DollarAmount.fromCents(value)
      def USD = DollarAmount.fromDollars(value)
    }
  }

  object Implicits extends Implicits
}
