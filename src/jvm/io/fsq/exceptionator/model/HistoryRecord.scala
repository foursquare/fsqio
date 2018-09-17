// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.model

import _root_.io.fsq.exceptionator.model.gen.{HistoryRecord, HistoryRecordProxy}
import _root_.io.fsq.exceptionator.util.Config
import _root_.io.fsq.rogue.lift.LiftRogue._
import net.liftweb.record.field._
import org.joda.time.DateTime
import scala.collection.JavaConverters.mapAsScalaConcurrentMapConverter

/** Stores one minute of sampled history. Uses the starting timestamp as its _id. */
class RichHistoryRecord(thriftModel: HistoryRecord) extends HistoryRecordProxy {
  override val underlying = thriftModel
}

object RichHistoryRecord {
  def apply(thriftModel: HistoryRecord) = new RichHistoryRecord(thriftModel)

  val windowSecs = Config.opt(_.getInt("history.sampleWindowSeconds")).getOrElse(60)
  val windowMillis = windowSecs * 1000L

  // round <base> down to 0 mod <mod>
  def roundMod(base: Long, mod: Long): Long = (base / mod) * mod
  def idForTime(date: DateTime) = new DateTime(roundMod(date.getMillis, windowMillis))
}
