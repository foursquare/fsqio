// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.model

import _root_.io.fsq.exceptionator.model.gen.{NoticeRecord, NoticeRecordProxy}
import _root_.io.fsq.rogue.lift.LiftRogue._
import java.util.Date
import net.liftweb.json.JsonDSL._
import net.liftweb.json._
import net.liftweb.record.field._
import org.joda.time.{DateTime, DateTimeZone}

class RichNoticeRecord(thriftModel: NoticeRecord) extends NoticeRecordProxy {
  override val underlying = thriftModel

  def createDateTime: DateTime = new DateTime(id.getTimestamp * 1000L, DateTimeZone.UTC)
  def createTime: Date = createDateTime.toDate
}

object RichNoticeRecord {
  def apply(thriftModel: NoticeRecord) = new RichNoticeRecord(thriftModel)
}
