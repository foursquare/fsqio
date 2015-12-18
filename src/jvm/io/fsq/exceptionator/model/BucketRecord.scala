// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.model

import _root_.io.fsq.exceptionator.util.Hash
import _root_.io.fsq.rogue._
import _root_.io.fsq.rogue.index.{Asc, IndexedRecord}
import _root_.io.fsq.rogue.lift.LiftRogue._
import net.liftweb.json._
import net.liftweb.mongodb.record.{MongoMetaRecord, MongoRecord}
import net.liftweb.mongodb.record.field.{MongoListField, MongoMapField}
import net.liftweb.record.field._
import org.bson.types.ObjectId
import org.joda.time.DateTime


class BucketRecord extends MongoRecord[BucketRecord] {
  def meta = BucketRecord
  override def id = this._id.value

  object _id extends StringField(this, 255) // name:key

  object notices extends MongoListField[BucketRecord, ObjectId](this) {
    override def name = "ids"
  }

  object firstSeen extends LongField(this) {
    override def name = "df"
  }

  object lastSeen extends LongField(this) {
    override def name = "dl"
  }

  object firstVersion extends StringField[BucketRecord](this, 50) {
    override def name = "vf"
  }

  object lastVersion extends StringField[BucketRecord](this, 50) {
    override def name = "vl"
  }

  object noticeCount extends IntField[BucketRecord](this) {
    override def name = "n"
    override def defaultValue = 1
    // atomically inc via modify clause
    override def dirty_? = if (value > 1) false else super.dirty_?
  }

}

object BucketRecord extends BucketRecord with MongoMetaRecord[BucketRecord] with IndexedRecord[BucketRecord] {
  override def collectionName = "buckets"

  val idIndex = BucketRecord.index(_._id, Asc)

  override val mongoIndexList = Vector(
    idIndex,
    BucketRecord.index(_.lastSeen, Asc)) // finding old buckets
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

class BucketRecordHistogram extends MongoRecord[BucketRecordHistogram] {
  def meta = BucketRecordHistogram
  override def id = this._id.value

  object _id extends StringField(this, 255) // timePrefix:name:key

  object histogram extends MongoMapField[BucketRecordHistogram, Int](this) {
    override def name = "h"
  }

  def bucket = id.substring(id.indexOf(':') + 1)

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
        dt.withHourOfDay(Hash.fieldNameDecode(id(2).toString))
          .withMonthOfYear(Hash.fieldNameDecode(id(0).toString))
          .withDayOfMonth(Hash.fieldNameDecode(id(1).toString))
      case HistogramType.Day =>
        dt.withHourOfDay(0)
          .withMonthOfYear(Hash.fieldNameDecode(id(0).toString))
          .withDayOfMonth(Hash.fieldNameDecode(id(1).toString))
      case HistogramType.Month =>
        dt.withHourOfDay(0)
          .withMonthOfYear(Hash.fieldNameDecode(id(0).toString))
          .withDayOfMonth(1)
    }
  }

  def toEpochMap(now: DateTime): Map[String, Int] = {
    val start = startTime(now).getMillis
    histogram.value.map{ case (k, v) => (start + Hash.fieldNameDecode(k) * histogramType.step).toString -> v }.toMap
  }
}

object BucketRecordHistogram
    extends BucketRecordHistogram
    with MongoMetaRecord[BucketRecordHistogram]
    with IndexedRecord[BucketRecordHistogram] {
  override def collectionName = "bucket_histograms"

  override val mongoIndexList = Vector(
    BucketRecord.index(_._id, Asc))

}
