// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.actions.concrete

import com.mongodb.WriteConcern
import com.twitter.ostrich.stats.Stats
import io.fsq.common.logging.Logger
import io.fsq.exceptionator.actions.NoticeActions
import io.fsq.exceptionator.model.{MongoOutgoing, RichNoticeRecord}
import io.fsq.exceptionator.model.gen.NoticeRecord
import io.fsq.exceptionator.model.io.{BucketId, Outgoing, RichIncoming}
import io.fsq.exceptionator.mongo.{ExceptionatorMongoService, HasExceptionatorMongoService}
import io.fsq.spindle.rogue.{SpindleQuery => Q}
import io.fsq.spindle.rogue.SpindleRogue._
import net.liftweb.json._
import org.bson.types.ObjectId
import org.joda.time.DateTime
import scala.collection.JavaConverters._

class ConcreteNoticeActions(
  services: HasExceptionatorMongoService
) extends NoticeActions
  with Logger {
  def executor: ExceptionatorMongoService.Executor = services.exceptionatorMongo.executor

  def get(ids: Seq[ObjectId]): Seq[Outgoing] = {
    executor
      .fetch(
        Q(NoticeRecord)
          .where(_.id in ids)
      )
      .unwrap
      .map(RichNoticeRecord(_))
      .map(MongoOutgoing(_))
  }

  def search(keywords: Seq[String], limit: Option[Int]) = {
    executor
      .fetch(
        Q(NoticeRecord)
          .where(_.keywords all keywords)
          .orderDesc(_.id)
          .limitOpt(limit)
      )
      .unwrap
      .map(RichNoticeRecord(_))
      .map(MongoOutgoing(_))
  }

  def save(
    incoming: RichIncoming,
    tags: Set[String],
    keywords: Set[String],
    buckets: Set[BucketId]
  ): RichNoticeRecord = {
    val objectId = new ObjectId(incoming.dateOption.map(new DateTime(_)).getOrElse(DateTime.now).toDate)
    val builder = NoticeRecord.newBuilder.id(objectId)

    builder
      .keywords(keywords.toList)
      .tags(tags.toList)
      .buckets(buckets.map(_.toString).toVector)
      .notice(incoming)

    val notice: NoticeRecord = executor.save(builder.result()).unwrap
    RichNoticeRecord(notice)
  }

  def addBucket(id: ObjectId, bucketId: BucketId) {
    Stats.time("incomingActions.addBucket") {
      executor.updateOne(
        Q(NoticeRecord)
          .where(_.id eqs id)
          .modify(_.buckets push bucketId.toString)
      )
    }
  }

  def removeBucket(id: ObjectId, bucketId: BucketId) {
    val updated = Stats.time("noticeActions.removeBucket") {
      executor
        .updateOne(
          Q(NoticeRecord)
            .where(_.id eqs id)
            .modify(_.buckets pull bucketId.toString)
        )
        .unwrap
    }

    if (updated > 0) {
      logger.debug("deleting " + id.toString)
      Stats.time("noticeActions.removeBucket.deleteRecord") {
        executor.findAndDeleteOne(
          Q(NoticeRecord)
            .where(_.id eqs id)
        )
      }
    }
  }

  override def removeExpiredNotices(now: DateTime): Seq[RichNoticeRecord] = {
    val expiredNotices = executor
      .fetch(
        Q(NoticeRecord)
          .where(_.expireAt before now)
      )
      .unwrap
      .map(RichNoticeRecord(_))

    executor.bulkDelete_!!(
      Q(NoticeRecord)
        .where(_.id in expiredNotices.map(_.id)),
      WriteConcern.W1
    )
    expiredNotices
  }
}
