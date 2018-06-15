// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.common.algorithm

import io.fsq.common.scala.Identity._
import java.util.concurrent.atomic.AtomicInteger

/**
  * Thread-safe exponential moving average counter. `coefficient` is a number between
  * 0 and 1 that determines to what degree the previous average is factored in when
  * constructing a new average (0 = use entirely the old average, aka average doesn't
  * change, 1 = use entirely the new average).
  */
class ExponentialMovingAverageCounter(val coefficient: Double) {
  @volatile private var average: Option[Double] = None

  private val current: AtomicInteger = new AtomicInteger(0)

  def getAverage(): Option[Double] = average

  def clearAverage(): Unit = this.synchronized {
    average = None
  }

  def increment(): Unit = {
    current.incrementAndGet()
  }

  // Acts as if the previous average were 0.
  def clearAndStartNewInterval(): Unit = {
    val count = current.getAndSet(0)
    this.synchronized {
      average = Some(coefficient * count)
    }
  }

  def startNewInterval(): Unit = {
    val count = current.getAndSet(0)
    this.synchronized {
      average = Some(average match {
        case Some(oldAverage) => (coefficient * count) + ((1 - coefficient) * oldAverage)
        case None => count
      })
    }
  }

  override def toString: String = s"EMA(current=${current}, average=${average})"
}
