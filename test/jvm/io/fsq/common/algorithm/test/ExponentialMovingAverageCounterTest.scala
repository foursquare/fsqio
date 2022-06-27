// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.common.algorithm.test

import io.fsq.common.algorithm.ExponentialMovingAverageCounter
import io.fsq.common.testing.matchers.{FoursquareMatchers => FM}
import org.hamcrest.MatcherAssert
import org.junit.{Assert => A, Test}

class ExponentialMovingAverageCounterTest {
  @Test
  def testNoAverage(): Unit = {
    val counter = new ExponentialMovingAverageCounter(0.5)
    MatcherAssert.assertThat(counter.getAverage(), FM.isNone[Double])
    counter.increment()
    MatcherAssert.assertThat(counter.getAverage(), FM.isNone[Double]) // Still None until you call startNewInterval().
  }

  @Test
  def testOneInterval(): Unit = {
    val counter = new ExponentialMovingAverageCounter(0.5)
    (0 until 5).foreach(_ => counter.increment())
    counter.startNewInterval()
    A.assertEquals(Some(5.0), counter.getAverage())
  }

  @Test
  def testClearAndStartNewInterval(): Unit = {
    val counter = new ExponentialMovingAverageCounter(0.5)
    (0 until 5).foreach(_ => counter.increment())
    counter.clearAndStartNewInterval()
    A.assertEquals(Some(2.5), counter.getAverage())
    counter.increment()
    counter.startNewInterval()
    A.assertEquals(Some(1.75), counter.getAverage())
  }

  @Test
  def testMultipleIntervals(): Unit = {
    val counter = new ExponentialMovingAverageCounter(0.5)
    MatcherAssert.assertThat(counter.getAverage(), FM.isNone[Double])

    (0 until 5).foreach(_ => counter.increment())
    counter.startNewInterval()
    A.assertEquals(Some(5.0), counter.getAverage())

    (0 until 10).foreach(_ => counter.increment())
    counter.startNewInterval()
    A.assertEquals(Some(7.5), counter.getAverage())

    (0 until 5).foreach(_ => counter.increment())
    counter.startNewInterval()
    A.assertEquals(Some(6.25), counter.getAverage()) // Average moves back towards 5.0
  }
}
