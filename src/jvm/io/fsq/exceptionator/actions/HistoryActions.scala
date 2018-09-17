// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.actions

import io.fsq.exceptionator.model.RichNoticeRecord
import io.fsq.exceptionator.model.io.Outgoing
import org.joda.time.DateTime

trait HasHistoryActions {
  def historyActions: HistoryActions
}

trait HistoryActions extends IndexActions {
  def get(bucketName: String, time: DateTime, limit: Int): Seq[Outgoing]
  def get(bucketName: String, bucketKey: String, time: DateTime, limit: Int): Seq[Outgoing]
  def get(ids: Seq[String], time: DateTime, limit: Int): Seq[Outgoing]
  def getGroupNotices(name: String, time: DateTime, limit: Int): Seq[RichNoticeRecord]
  def getNotices(buckets: Seq[String], time: DateTime, limit: Int): Seq[RichNoticeRecord]
  def oldestId: Option[DateTime]
  def removeExpiredNotices(now: DateTime): Unit
  def save(notice: RichNoticeRecord): DateTime
}
