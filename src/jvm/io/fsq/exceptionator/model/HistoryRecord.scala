// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.model

import _root_.io.fsq.exceptionator.util.Config
import _root_.io.fsq.rogue._
import _root_.io.fsq.rogue.index.{Asc, Desc, IndexedRecord}
import _root_.io.fsq.rogue.lift.LiftRogue._
import net.liftweb.common.Box
import net.liftweb.mongodb.record.{MongoMetaRecord, MongoRecord}
import net.liftweb.mongodb.record.field.{BsonRecordListField, DateField, MongoListField}
import net.liftweb.record.field._
import org.joda.time.DateTime
import scala.collection.JavaConverters.mapAsScalaConcurrentMapConverter


/** Stores one minute of sampled history. Uses the starting timestamp as its _id. */
class HistoryRecord extends MongoRecord[HistoryRecord] {
  def meta = HistoryRecord

  object id extends DateField[HistoryRecord](this) {
    override def name = "_id"
    def apply(in: DateTime): HistoryRecord = apply(in.toDate)
    def dateTimeValue: DateTime = new DateTime(value)
  }

  object notices extends BsonRecordListField[HistoryRecord, NoticeRecord](this, NoticeRecord) {
    override def name = "n"
  }

  object buckets extends MongoListField[HistoryRecord, String](this) {
    override def name = "b"
    // mongo 2.6 and above enforces an index key length of < 1024 bytes. do that filtering here
    override def setBox(in: Box[List[String]]): Box[List[String]] = {
      super.setBox(in.map(_.filter(_.length < 256)))
    }
  }

  object sampleRate extends IntField(this) {
    override def name = "r"
  }

  object window extends IntField(this) {
    override def name = "w"
    override def defaultValue = HistoryRecord.windowSecs
  }

  object totalSampled extends IntField(this) {
    override def name = "s"
  }
}

object HistoryRecord extends HistoryRecord with MongoMetaRecord[HistoryRecord] with IndexedRecord[HistoryRecord] {
  override def collectionName = "history"

  val windowSecs = Config.opt(_.getInt("history.sampleWindowSeconds")).getOrElse(60)
  val windowMillis = windowSecs * 1000L

  // round <base> down to 0 mod <mod>
  def roundMod(base: Long, mod: Long): Long = (base/mod) * mod
  def idForTime(date: DateTime): DateTime = new DateTime(roundMod(date.getMillis, windowMillis))

  val bucketIdIndex = HistoryRecord.index(_.buckets, Asc, _.id, Desc)

  override val mongoIndexList = Vector(
    HistoryRecord.index(_.id, Asc),
    bucketIdIndex)
}
