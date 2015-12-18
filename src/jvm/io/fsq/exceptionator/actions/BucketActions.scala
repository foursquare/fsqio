// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.actions

import io.fsq.exceptionator.model.BucketRecordHistogram
import io.fsq.exceptionator.model.io.{BucketId, Incoming, Outgoing}
import org.bson.types.ObjectId
import org.joda.time.DateTime

trait HasBucketActions {
  def bucketActions: BucketActions
}

case class SaveResult(bucket: BucketId, oldResult: Option[BucketId], noticesToRemove: List[ObjectId])

trait BucketActions extends IndexActions {
  def get(ids: List[String], noticesPerBucketLimit: Option[Int], now: DateTime): List[Outgoing]
  def get(name: String, key: String, now: DateTime): List[Outgoing]
  def getHistograms(
    ids: List[String],
    now: DateTime,
    includeMonth: Boolean,
    includeDay: Boolean,
    includeHour: Boolean): List[BucketRecordHistogram]
  def recentKeys(name: String, limit: Option[Int]): List[String]
  def lastHourHistogram(id: BucketId, now: DateTime): List[Int]
  def save(incomingId: ObjectId, incoming: Incoming, bucket: BucketId, maxRecent: Int): SaveResult
  def deleteOldHistograms(time: Long, doIt: Boolean = true): Unit
  def deleteOldBuckets(lastUpdatedTime: Long, batchSize: Int = 500, doIt: Boolean = true): List[SaveResult]
}
