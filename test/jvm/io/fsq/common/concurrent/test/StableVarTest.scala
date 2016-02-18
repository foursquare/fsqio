// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.common.concurrent.test

import com.twitter.util.{Await, Var, Witness}
import io.fsq.common.concurrent.{StableEvent, StableVar}
import java.util.concurrent.atomic.AtomicInteger
import org.junit.{Assert => A, Test}

class StableVarTest {
  @Test
  def testStableEvent(): Unit = {
    val v = Var(0)

    val count = new AtomicInteger(0)
    val countWitness = new Witness[Int] {
      override def notify(newValue: Int): Unit = {
        count.incrementAndGet()
      }
    }

    val stableChanges = new StableEvent(v.changes)

    val c = stableChanges.register(countWitness)
    try {
      A.assertEquals(1, count.get())

      v.update(1)
      A.assertEquals(2, count.get())

      v.update(1)
      A.assertEquals(2, count.get())

      v.update(0)
      A.assertEquals(3, count.get())
    } finally {
      Await.ready(c.close())
    }
  }

  @Test
  def testStableVar(): Unit = {
    val v = Var(0)
    val stable = StableVar(0, v)

    val count = new AtomicInteger(0)
    val countWitness = new Witness[Int] {
      override def notify(newValue: Int): Unit = {
        count.incrementAndGet()
      }
    }

    val stableChanges = stable.changes

    val c = stableChanges.register(countWitness)
    try {
      A.assertEquals(0, stable.sample())
      A.assertEquals(1, count.get())

      v.update(1)
      A.assertEquals(1, stable.sample())
      A.assertEquals(2, count.get())

      v.update(1)
      A.assertEquals(1, stable.sample())
      A.assertEquals(2, count.get())

      v.update(0)
      A.assertEquals(0, stable.sample())
      A.assertEquals(3, count.get())
    } finally {
      Await.ready(c.close())
    }

  }
}
