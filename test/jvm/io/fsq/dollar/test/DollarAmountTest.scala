// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.dollar.test

import io.fsq.dollar.DollarAmount
import io.fsq.dollar.DollarAmount.Implicits._
import org.junit.{Assert, Test}

class DollarAmountTest {

  @Test
  def testDollarAmountToString(): Unit = {
    val testCases = List(
      DollarAmount.fromMicros(0).toString() -> "$0",
      DollarAmount.fromMicros(10000).toString() -> "$0.01",
      DollarAmount.fromMicros(10001).toString() -> "$0.01",
      DollarAmount.fromMicros(1000000).toString() -> "$1",
      DollarAmount.fromMicros(1000000).toString(forceCents = true) -> "$1.00",
      DollarAmount.fromMicros(1500000).toString() -> "$1.50",
      DollarAmount.fromMicros(240000000L).toString() -> "$240",
      DollarAmount.fromMicros(2400000000L).toString() -> "$2400",
      DollarAmount.fromMicros(2400000000L).toString(forceCents = false, commaify = true) -> "$2,400",
      DollarAmount.fromMicros(2400000000L).toString(forceCents = true, commaify = true) -> "$2,400.00",
      DollarAmount.fromMicros(2400180000L).toString(forceCents = false, commaify = true) -> "$2,400.18",
      DollarAmount.fromMicros(2400180000L).toString(forceCents = true, commaify = true) -> "$2,400.18",
      DollarAmount.fromMicros(-10000).toString() -> "-$0.01",
      DollarAmount.fromMicros(-10001).toString() -> "-$0.01",
      DollarAmount.fromMicros(-1000000).toString() -> "-$1",
      DollarAmount.fromMicros(-1000000).toString(forceCents = true) -> "-$1.00",
      DollarAmount.fromMicros(-1500000).toString() -> "-$1.50",
      DollarAmount.fromMicros(-240000000L).toString() -> "-$240",
      DollarAmount.fromMicros(-2400000000L).toString() -> "-$2400",
      DollarAmount.fromMicros(-2400000000L).toString(forceCents = false, commaify = true) -> "-$2,400",
      DollarAmount.fromMicros(-2400000000L).toString(forceCents = true, commaify = true) -> "-$2,400.00",
      DollarAmount.fromMicros(-2400180000L).toString(forceCents = false, commaify = true) -> "-$2,400.18",
      DollarAmount.fromMicros(-2400180000L).toString(forceCents = true, commaify = true) -> "-$2,400.18"
    )

    testCases.foreach {
      case (actual, expected) =>
        Assert.assertEquals(expected, actual)
    }
  }

  @Test
  def testDollarAmounToStringRoundTrip(): Unit = {
    val testCases = List(-682022, 847010, -641170, 88838, 23400, -2948100)
    testCases.foreach(c => {
      val d = DollarAmount.fromCents(c)
      Assert.assertEquals(d, DollarAmount.fromDollars(d.toString()))
      Assert.assertEquals(d, DollarAmount.fromDollars(d.toString(forceCents = true)))
      Assert.assertEquals(d, DollarAmount.fromDollars(d.toString(forceCents = true, commaify = true)))
      Assert.assertEquals(d, DollarAmount.fromDollars(d.toString(forceCents = false, commaify = true)))
    })
  }

  @Test
  def testRounding(): Unit = {
    def x(cents: Int, hundredths: Int): DollarAmount = {
      DollarAmount.fromCents(cents) + DollarAmount.fromMicros(hundredths * DollarAmount.centShift / 100)
    }

    // Specific values
    Assert.assertEquals(x(10, 0).roundedHalfEvenToCents, 10.centsUSD)
    Assert.assertEquals(x(10, 10).roundedHalfEvenToCents, 10.centsUSD)
    Assert.assertEquals(x(10, 50).roundedHalfEvenToCents, 10.centsUSD)
    Assert.assertEquals(x(10, 70).roundedHalfEvenToCents, 11.centsUSD)
    Assert.assertEquals(x(11, 0).roundedHalfEvenToCents, 11.centsUSD)
    Assert.assertEquals(x(11, 10).roundedHalfEvenToCents, 11.centsUSD)
    Assert.assertEquals(x(11, 50).roundedHalfEvenToCents, 12.centsUSD)
    Assert.assertEquals(x(11, 90).roundedHalfEvenToCents, 12.centsUSD)
    Assert.assertEquals(x(12, 0).roundedHalfEvenToCents, 12.centsUSD)

    Assert.assertEquals(x(12, 1).roundedHalfEvenToCents, 12.centsUSD)
    Assert.assertEquals(x(12, 49).roundedHalfEvenToCents, 12.centsUSD)
    Assert.assertEquals(x(12, 50).roundedHalfEvenToCents, 12.centsUSD)
    Assert.assertEquals(x(12, 51).roundedHalfEvenToCents, 13.centsUSD)
    Assert.assertEquals(x(12, 75).roundedHalfEvenToCents, 13.centsUSD)
    Assert.assertEquals(x(13, 5).roundedHalfEvenToCents, 13.centsUSD)

    // Sum of rounded values should be same as sum of non-rounded values
    var rounded: DollarAmount = DollarAmount.zero
    var notRounded: DollarAmount = DollarAmount.zero

    for {
      i <- 0 to 100
      j <- 0 to 100
    } {
      rounded = rounded + x(i, j).roundedHalfEvenToCents
      notRounded = notRounded + x(i, j)
    }

    Assert.assertEquals(rounded.roundedHalfEvenToCents, notRounded.roundedHalfEvenToCents)
  }
}
