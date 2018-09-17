// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.actions

import io.fsq.exceptionator.model.RichNoticeRecord
import io.fsq.exceptionator.model.io.{BucketId, Outgoing, RichIncoming}
import org.bson.types.ObjectId
import org.joda.time.DateTime

trait HasNoticeActions {
  def noticeActions: NoticeActions
}

trait NoticeActions extends IndexActions {
  def get(ids: Seq[ObjectId]): Seq[Outgoing]
  def search(keywords: Seq[String], limit: Option[Int]): Seq[Outgoing]
  def save(
    incoming: RichIncoming,
    tags: Set[String],
    keywords: Set[String],
    buckets: Set[BucketId]
  ): RichNoticeRecord
  def addBucket(id: ObjectId, bucketId: BucketId): Unit
  def removeBucket(id: ObjectId, bucketId: BucketId): Unit
  def removeExpiredNotices(now: DateTime): Seq[RichNoticeRecord]
}
