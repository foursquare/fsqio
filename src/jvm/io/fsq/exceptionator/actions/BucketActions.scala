// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.actions

import io.fsq.exceptionator.model.{RichBucketRecordHistogram, RichNoticeRecord}
import io.fsq.exceptionator.model.io.{BucketId, Outgoing, RichIncoming}
import org.bson.types.ObjectId
import org.joda.time.DateTime

trait HasBucketActions {
  def bucketActions: BucketActions
}

case class SaveResult(bucket: BucketId, oldResult: Option[BucketId], noticesToRemove: Seq[ObjectId])

trait BucketActions {
  def get(ids: Seq[String], noticesPerBucketLimit: Option[Int], now: DateTime): Seq[Outgoing]
  def get(name: String, key: String, now: DateTime): Seq[Outgoing]
  def getHistograms(
    ids: Seq[String],
    now: DateTime,
    includeMonth: Boolean,
    includeDay: Boolean,
    includeHour: Boolean
  ): Seq[RichBucketRecordHistogram]
  def recentKeys(name: String, limit: Option[Int]): Seq[String]
  def lastHourHistogram(id: BucketId, now: DateTime): Seq[Int]
  def save(incomingId: ObjectId, incoming: RichIncoming, bucket: BucketId, maxRecent: Int): SaveResult
  def deleteOldHistograms(time: DateTime, doIt: Boolean = true): Unit
  def deleteOldBuckets(lastUpdatedTime: DateTime, batchSize: Int = 500, doIt: Boolean = true): Seq[SaveResult]
  def removeExpiredNotices(notices: Seq[RichNoticeRecord]): Unit
}
