// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.model

import _root_.io.fsq.exceptionator.model.io.Incoming
import _root_.io.fsq.rogue._
import _root_.io.fsq.rogue.index.{Asc, IndexedRecord}
import _root_.io.fsq.rogue.lift.LiftRogue._
import java.util.Date
import net.liftweb.common.Box
import net.liftweb.json._
import net.liftweb.mongodb.record.{MongoMetaRecord, MongoRecord}
import net.liftweb.mongodb.record.field.{MongoCaseClassField, MongoListField, ObjectIdPk}
import net.liftweb.record.field._
import org.bson.types.ObjectId
import org.joda.time.{DateTime, DateTimeZone}


class NoticeRecord extends MongoRecord[NoticeRecord] with ObjectIdPk[NoticeRecord] {
  def meta = NoticeRecord

  def createDateTime = new DateTime(id.value.getTimestamp * 1000L, DateTimeZone.UTC)
  def createTime = createDateTime.toDate

  object notice extends MongoCaseClassField[NoticeRecord, Incoming](this) {
    override def name = "n"
  }

  object tags extends MongoListField[NoticeRecord, String](this) {
    override def name = "t"
  }

  object keywords extends MongoListField[NoticeRecord, String](this) {
    override def name = "kw"
    // mongo 2.6 and above enforces an index key length of < 1024 bytes. do that filtering here
    override def setBox(in: Box[List[String]]): Box[List[String]] = {
      super.setBox(in.map(_.filter(_.length < 256)))
    }
  }

  object buckets extends MongoListField[NoticeRecord, String](this) {
    override def name = "b"
  }
}

object NoticeRecord extends NoticeRecord with MongoMetaRecord[NoticeRecord] with IndexedRecord[NoticeRecord] {
  override def collectionName = "notices"

  val keywordIndex = NoticeRecord.index(_.keywords, Asc)
  override val mongoIndexList = Vector(
    NoticeRecord.index(_.id, Asc),
    keywordIndex)

  def createRecordFrom(incoming: Incoming): NoticeRecord = {
    val rec = createRecord.notice(incoming)
    incoming.d.foreach(epoch => rec.id(new ObjectId(new Date(epoch))))
    rec
  }
}
