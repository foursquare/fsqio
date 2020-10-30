// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.net.stats.test

import io.fsq.net.stats.FoursquareStatsReceiver
import io.fsq.twitter.ostrich.stats.StatsProvider
import org.junit.Test
import org.mockito.Mockito.{mock, times, verify}

class FoursquareStatsReceiverTest {
  @Test
  def testBuilding1: Unit = {
    val sp = mock(classOf[StatsProvider])
    val rec1 = new FoursquareStatsReceiver(Nil, sp)
    rec1.counter("a", "b").incr()
    rec1.counter().incr()

    verify(sp, times(1)).incr("a.b", 1)
    verify(sp, times(1)).incr("", 1)
  }

  @Test
  def testBuilding2: Unit = {
    val sp = mock(classOf[StatsProvider])
    val rec2 = new FoursquareStatsReceiver(Vector("a"), sp)
    rec2.counter().incr()
    rec2.counter("b").incr()
    rec2.counter("b", "c").incr()

    verify(sp, times(1)).incr("a", 1)
    verify(sp, times(1)).incr("a.b", 1)
    verify(sp, times(1)).incr("a.b.c", 1)
  }

  @Test
  def testBuilding3: Unit = {
    val sp = mock(classOf[StatsProvider])
    val rec3 = new FoursquareStatsReceiver(Vector("a", "b.x"), sp)
    rec3.counter().incr()
    rec3.counter("c").incr()
    rec3.counter("c", "d.y").incr()

    verify(sp, times(1)).incr("a.b_x", 1)
    verify(sp, times(1)).incr("a.b_x.c", 1)
    verify(sp, times(1)).incr("a.b_x", 1)
    verify(sp, times(1)).incr("a.b_x.c.d_y", 1)
  }
}
