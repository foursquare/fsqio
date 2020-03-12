// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.net.stats

import com.twitter.finagle.stats.{Counter, Stat, StatsReceiverWithCumulativeGauges}
import com.twitter.ostrich.stats.{Stats, StatsProvider}

/**
  * An adapter from our stats-gathering interface to finagle's StatsReceiver interface.
  *
  * Note that our stats-gathering interface is just an ostrich StatsProvider, and Finagle already comes with an
  * OstrichStatsReceiver. Unfortunately, however, that class talks directly to the global Stats object, and
  * we may want to override with a custom StatsProvider.
  */
class FoursquareStatsReceiver(
  prefix: Seq[String] = Nil,
  statsProvider: StatsProvider = Stats
) extends StatsReceiverWithCumulativeGauges {

  override val repr: AnyRef = statsProvider

  override protected[this] def registerGauge(name: Seq[String], f: => Float): Unit = {
    statsProvider.addGauge(variableName(name)) {
      f.toDouble
    }
  }

  override protected[this] def deregisterGauge(name: Seq[String]): Unit = {
    statsProvider.clearGauge(variableName(name))
  }

  override def counter(name: String*): Counter = new Counter {
    override def incr(delta: Int): Unit = {
      statsProvider.incr(variableName(name), delta)
    }
  }

  override def stat(name: String*): Stat = new Stat {
    override def add(value: Float): Unit = {
      statsProvider.addMetric(variableName(name), value.toInt)
    }
  }

  private[this] val baseSize = prefix.foldLeft(0)((sum, str) => sum + str.length) + prefix.size

  /*
   * NOTE(jackson): this method gets called a lot and was generating too much garbage. Some scalay-ness has been
   * sacrificed to make it more efficient (no scala collection recopy sillyness, no StringBuilder reallocs).
   * Still generates 2 char[] (1 in StringBuilder and 1 when copied to String), but fixing that requires lots of tricks
   */
  private[this] def variableName(name: Seq[String]): String = {
    val newSize = name.foldLeft(0)((sum, str) => sum + str.length) + name.size
    val sb = new StringBuilder(baseSize + newSize + 1)

    prefix.view.map(_.replace('.', '_')).addString(sb, "", ".", "")
    if (sb.length > 0 && !name.isEmpty)
      sb.append('.')
    name.view.map(_.replace('.', '_')).addString(sb, "", ".", "")
    sb.toString()
  }
}
