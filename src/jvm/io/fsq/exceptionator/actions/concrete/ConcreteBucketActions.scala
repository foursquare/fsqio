// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.actions.concrete

import com.twitter.ostrich.stats.Stats
import io.fsq.common.logging.Logger
import io.fsq.exceptionator.actions.{BucketActions, SaveResult}
import io.fsq.exceptionator.model.{MongoOutgoing, RichBucketRecord, RichBucketRecordHistogram, RichNoticeRecord}
import io.fsq.exceptionator.model.gen.{BucketRecord, BucketRecordHistogram, NoticeRecord}
import io.fsq.exceptionator.model.io.{BucketId, Outgoing, RichIncoming}
import io.fsq.exceptionator.mongo.{ExceptionatorMongoService, HasExceptionatorMongoService}
import io.fsq.exceptionator.util.Hash
import io.fsq.spindle.rogue.{SpindleQuery => Q}
import io.fsq.spindle.rogue.SpindleRogue._
import java.util.regex.Pattern
import org.bson.types.ObjectId
import org.joda.time.DateTime
import scala.collection.JavaConverters._
import scala.collection.immutable.NumericRange

class ConcreteBucketActions(
  services: HasExceptionatorMongoService
) extends BucketActions
  with Logger {
  var currentTime: Long = 0
  var lastHistogramTrim: Long = 0
  def executor: ExceptionatorMongoService.Executor = services.exceptionatorMongo.executor

  def getHistograms(
    ids: Seq[String],
    now: DateTime,
    includeMonth: Boolean,
    includeDay: Boolean,
    includeHour: Boolean
  ): Seq[RichBucketRecordHistogram] = {

    def monthFmt(t: DateTime) = Hash.fieldNameEncode(t.getMonthOfYear)
    def dayFmt(t: DateTime) = Hash.fieldNameEncode(t.getMonthOfYear) + Hash.fieldNameEncode(t.getDayOfMonth)
    def hourFmt(t: DateTime) = {
      Hash.fieldNameEncode(t.getMonthOfYear) +
        Hash.fieldNameEncode(t.getDayOfMonth) +
        Hash.fieldNameEncode(t.getHourOfDay)
    }

    // We want to show the last hour, last day, last month, but that will always span
    // two buckets except for one (minute, hour, day) of the (hour, day, month)
    val bucketHistogramIds: Set[String] = (
      // Last 2 months
      (if (includeMonth) {
         Set(now.minusMonths(1), now)
           .map(monthFmt _)
           .flatMap(month => ids.map("%s:%s".format(month, _)))
       } else {
         Set.empty
       })

        ++

          // Last 2 days
          (if (includeDay) {
             Set(now.minusDays(1), now)
               .map(dayFmt _)
               .flatMap(day => ids.map("%s:%s".format(day, _)))
           } else {
             Set.empty
           })

        ++

          // Last 2 hours
          (if (includeHour) {
             Set(now.minusHours(1), now)
               .map(hourFmt _)
               .flatMap(hour => ids.map("%s:%s".format(hour, _)))
           } else {
             Set.empty
           })
    )

    executor
      .fetch(
        Q(BucketRecordHistogram)
          .where(_.id in bucketHistogramIds)
      )
      .unwrap
      .map(RichBucketRecordHistogram(_))
  }

  def get(ids: Seq[String], noticesPerBucketLimit: Option[Int], now: DateTime): Seq[Outgoing] = {
    val buckets = executor
      .fetch(
        Q(BucketRecord)
          .where(_.id in ids)
      )
      .unwrap
      .map(RichBucketRecord(_))

    val noticeIds = buckets
      .flatMap(bucket => {
        noticesPerBucketLimit match {
          case Some(limit) => bucket.notices.takeRight(limit)
          case None => bucket.notices
        }
      })
      .toSet

    val histograms = getHistograms(ids, now, true, true, true)
    val notices = executor
      .fetch(
        Q(NoticeRecord)
          .where(_.id in noticeIds)
      )
      .unwrap
      .map(RichNoticeRecord(_))

    notices
      .sortBy(_.id)
      .reverse
      .map(n => {
        val nbSet = n.buckets.toSet
        val noticeBuckets = buckets.filter(b => nbSet(b.id))
        val noticeBucketRecordHistograms = histograms.filter(h => nbSet(h.bucket))
        MongoOutgoing(n).addBuckets(noticeBuckets, noticeBucketRecordHistograms, now)
      })
  }

  def lastHourHistogram(id: BucketId, now: DateTime): Seq[Int] = {
    val fullMap = getHistograms(List(id.toString), now, false, false, true)
      .map(_.toEpochMap(now))
      .foldLeft(Map[String, Int]()) { _ ++ _ }
    val oneHourAgo = now
      .minusHours(1)
      .withSecondOfMinute(0)
      .withMillisOfSecond(0)
    NumericRange[Long](oneHourAgo.getMillis, oneHourAgo.plusHours(1).getMillis, 60 * 1000L)
      .map(t => {
        fullMap.get(t.toString).getOrElse(0)
      })
      .toList
  }

  def get(name: String, key: String, now: DateTime) = {
    get(List(BucketId(name, key).toString), None, now)
  }

  def recentKeys(name: String, limit: Option[Int]): Seq[String] = {
    executor
      .fetch(
        Q(BucketRecord)
          .where(_.id startsWith name + ":")
          .select(_.id)
          .orderDesc(_.lastSeen)
          .limitOpt(limit)
      )
      .unwrap
      .flatten
  }

  def save(incomingId: ObjectId, incoming: RichIncoming, bucket: BucketId, maxRecent: Int): SaveResult = {
    val n = incoming.countOption.getOrElse(1)

    val dateTime = new DateTime(incomingId.getTimestamp * 1000L)
    val month = Hash.fieldNameEncode(dateTime.getMonthOfYear)
    val day = Hash.fieldNameEncode(dateTime.getDayOfMonth)
    val hour = Hash.fieldNameEncode(dateTime.getHourOfDay)
    val minute = Hash.fieldNameEncode(dateTime.getMinuteOfHour)
    val bucketKey = bucket.toString

    val existing = Stats.time("bucketActions.save.updateBucket") {
      executor
        .findAndUpsertOne(
          Q(BucketRecord)
            .where(_.id eqs bucketKey)
            .findAndModify(_.noticeCount inc n)
            .findAndModify(_.lastSeen setTo incomingId.getTimestamp * 1000L)
            .findAndModify(_.lastVersion setTo incoming.versionOption)
            .findAndModify(_.notices push incomingId)
        )
        .unwrap
    }

    if (!existing.isDefined) {
      Stats.time("bucketActions.save.upsertBucket") {
        executor.upsertOne(
          Q(BucketRecord)
            .where(_.id eqs bucketKey)
            .modify(_.firstSeen setTo incomingId.getTimestamp * 1000L)
            .modify(_.firstVersion setTo incoming.versionOption)
        )
      }
    }

    val noticesToRemove = existing.toList.flatMap(e => {
      val len = e.notices.length
      if (len >= maxRecent + maxRecent / 2) {
        logger.debug("trimming %d from %s".format(len - maxRecent, bucketKey))
        val toRemove = e.notices.take(len - maxRecent)
        Stats.time("bucketActions.save.removeExpiredNotices") {
          executor.updateOne(
            Q(BucketRecord)
              .where(_.id eqs bucketKey)
              .modify(_.notices pullAll toRemove)
          )
        }
        toRemove
      } else {
        Nil
      }
    })

    val bucketHour = "%s%s%s:%s".format(month, day, hour, bucketKey)
    val bucketDay = "%s%s:%s".format(month, day, bucketKey)
    val bucketMonth = "%s:%s".format(month, bucketKey)
    Stats.time("bucketActions.save.updateHistogram") {
      executor.upsertOne(
        Q(BucketRecordHistogram)
          .where(_.id eqs bucketHour)
          .modify(_.histogram at minute inc n)
      )
      executor.upsertOne(
        Q(BucketRecordHistogram)
          .where(_.id eqs bucketDay)
          .modify(_.histogram at hour inc n)
      )
      executor.upsertOne(
        Q(BucketRecordHistogram)
          .where(_.id eqs bucketMonth)
          .modify(_.histogram at day inc n)
      )
    }

    val oldNoticeCount = existing.map(_.noticeCount).getOrElse(0)

    SaveResult(bucket.count(oldNoticeCount + 1), existing.map(b => BucketId(b.id, oldNoticeCount)), noticesToRemove)
  }

  override def deleteOldHistograms(time: DateTime, doIt: Boolean = true): Unit = {
    val oldMonth = time.minusMonths(2)
    val oldDay = time.minusDays(2)
    val oldHour = time.minusHours(2)

    val oldMonthField = Hash.fieldNameEncode(oldMonth.getMonthOfYear)

    val oldDaysPattern = Pattern.compile(
      "^%s[%s]".format(
        Hash.fieldNameEncode(oldDay.getMonthOfYear),
        (0 to oldDay.getDayOfMonth).map(Hash.fieldNameEncode _).mkString("")
      )
    )

    val oldHoursPattern = Pattern.compile(
      "^%s%s[%s]".format(
        Hash.fieldNameEncode(oldHour.getMonthOfYear),
        Hash.fieldNameEncode(oldHour.getDayOfMonth),
        (0 to oldHour.getHourOfDay).map(Hash.fieldNameEncode _).mkString("")
      )
    )

    if (doIt) {
      Stats.time("bucketActions.save.deleteOldHistograms.delete") {
        executor.bulkDelete_!!(
          Q(BucketRecordHistogram)
            .where(_.id startsWith oldMonthField)
        )

        executor.bulkDelete_!!(
          Q(BucketRecordHistogram)
            .where(_.id matches oldDaysPattern)
        )

        executor.bulkDelete_!!(
          Q(BucketRecordHistogram)
            .where(_.id matches oldHoursPattern)
        )
      }
    }
  }

  override def deleteOldBuckets(time: DateTime, batchSize: Int = 500, doIt: Boolean = true): Seq[SaveResult] = {
    val staleDateTime = time.minusMonths(2).minusDays(1)
    val staleTime = staleDateTime.getMillis
    val oldBuckets = executor
      .fetch(
        Q(BucketRecord)
          .where(_.lastSeen lte staleTime)
          .limit(batchSize)
      )
      .unwrap

    if (!oldBuckets.isEmpty) {
      logger.info("Deleting %d old buckets since %s".format(oldBuckets.length, staleDateTime.toString()))
      val toRemoveResult = oldBuckets.map(b => SaveResult(BucketId(b.id), None, b.notices))
      val bucketIds = oldBuckets.map(_.id)
      if (doIt) {
        Stats.time("bucketActions.deleteOldBuckets.delete") {
          executor.fetch(
            Q(BucketRecord)
              .where(_.id in bucketIds)
          )
        }
      }
      toRemoveResult
    } else {
      Nil
    }
  }

  override def removeExpiredNotices(notices: Seq[RichNoticeRecord]): Unit = {
    val bucketIds = notices.flatMap(_.buckets)
    val noticeIds = notices.map(_.id)
    executor.updateMany(
      Q(BucketRecord)
        .where(_.id in bucketIds)
        .modify(_.notices pullAll noticeIds)
    )
  }
}
