// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.model

import _root_.io.fsq.exceptionator.model.gen.{
  BucketRecord,
  BucketRecordHistogram,
  BucketRecordHistogramProxy,
  BucketRecordProxy
}
import _root_.io.fsq.exceptionator.util.Hash
import _root_.io.fsq.rogue.lift.LiftRogue._
import net.liftweb.json._
import net.liftweb.record.field._
import org.joda.time.DateTime

class RichBucketRecord(thriftModel: BucketRecord) extends BucketRecordProxy {
  override val underlying = thriftModel
}

object RichBucketRecord {
  def apply(thriftModel: BucketRecord) = new RichBucketRecord(thriftModel)
}

object HistogramType {
  sealed trait HistogramType {
    def step: Int
  }
  case object Hour extends HistogramType {
    def step: Int = 60 * 1000
  }
  case object Day extends HistogramType {
    def step: Int = 60 * 60 * 1000
  }
  case object Month extends HistogramType {
    def step: Int = 24 * 60 * 60 * 1000
  }
}

class RichBucketRecordHistogram(thriftModel: BucketRecordHistogram) extends BucketRecordHistogramProxy {
  override val underlying = thriftModel

  def bucket: String = id.substring(id.indexOf(':') + 1)

  def histogramType = id.indexOf(':') match {
    case 3 => HistogramType.Hour
    case 2 => HistogramType.Day
    case 1 => HistogramType.Month
    case _ => throw new Exception("unexpected id formation %s".format(id))
  }

  def startTime(now: DateTime) = {
    val dt = now.withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0)
    // Set the month _before_ the day, e.g now is in November (30 days), but you are adjusting to 10/31.
    histogramType match {
      case HistogramType.Hour =>
        dt.withHourOfDay(Hash.fieldNameDecode(id.charAt(2).toString))
          .withMonthOfYear(Hash.fieldNameDecode(id.charAt(0).toString))
          .withDayOfMonth(Hash.fieldNameDecode(id.charAt(1).toString))
      case HistogramType.Day =>
        dt.withHourOfDay(0)
          .withMonthOfYear(Hash.fieldNameDecode(id.charAt(0).toString))
          .withDayOfMonth(Hash.fieldNameDecode(id.charAt(1).toString))
      case HistogramType.Month =>
        dt.withHourOfDay(0)
          .withMonthOfYear(Hash.fieldNameDecode(id.charAt(0).toString))
          .withDayOfMonth(1)
    }
  }

  def toEpochMap(now: DateTime): Map[String, Int] = {
    val start = startTime(now).getMillis
    histogram.map { case (k, v) => (start + Hash.fieldNameDecode(k) * histogramType.step).toString -> v }.toMap
  }
}

object RichBucketRecordHistogram {
  def apply(thriftModel: BucketRecordHistogram) = new RichBucketRecordHistogram(thriftModel)
}
