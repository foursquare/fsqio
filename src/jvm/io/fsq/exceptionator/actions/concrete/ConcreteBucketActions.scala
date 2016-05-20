// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.actions.concrete

import com.twitter.ostrich.stats.Stats
import io.fsq.common.logging.Logger
import io.fsq.exceptionator.actions.{BucketActions, IndexActions, SaveResult}
import io.fsq.exceptionator.model.{BucketRecord, BucketRecordHistogram, MongoOutgoing, NoticeRecord}
import io.fsq.exceptionator.model.io.{BucketId, Incoming, Outgoing}
import io.fsq.exceptionator.util.Hash
import io.fsq.rogue.lift.LiftRogue._
import java.util.regex.Pattern
import net.liftweb.json._
import org.bson.types.ObjectId
import org.joda.time.DateTime
import scala.collection.JavaConverters._
import scala.collection.immutable.NumericRange



class ConcreteBucketActions extends BucketActions with IndexActions with Logger {
  var currentTime: Long = 0
  var lastHistogramTrim: Long = 0


  def ensureIndexes {
    Vector(BucketRecord, BucketRecordHistogram).foreach(metaRecord => {
        metaRecord.mongoIndexList.foreach(i =>
          metaRecord.createIndex(JObject(i.asListMap.map(fld => JField(fld._1, JInt(fld._2.toString.toInt))).toList)))
    })
  }

  def getHistograms(
    ids: List[String],
    now: DateTime,
    includeMonth: Boolean,
    includeDay: Boolean,
    includeHour: Boolean): List[BucketRecordHistogram] = {

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
        Set(now.minusMonths(1), now).map(monthFmt _)
        .flatMap(month => ids.map("%s:%s".format(month, _)))
      } else {
        Set.empty
      })

      ++

      // Last 2 days
      (if (includeDay) {
      Set(now.minusDays(1), now).map(dayFmt _)
        .flatMap(day => ids.map("%s:%s".format(day, _)))
      } else {
        Set.empty
      })

      ++

      // Last 2 hours
      (if (includeHour) {
        Set(now.minusHours(1), now).map(hourFmt _)
        .flatMap(hour => ids.map("%s:%s".format(hour, _)))
      } else {
        Set.empty
      })
    )
    BucketRecordHistogram.where(_._id in bucketHistogramIds).fetch
  }

  def get(ids: List[String], noticesPerBucketLimit: Option[Int], now: DateTime): List[Outgoing] = {
    val buckets = BucketRecord.where(_._id in ids).fetch
    val noticeIds = buckets.flatMap(bucket => {
      noticesPerBucketLimit match {
        case Some(limit) => bucket.notices.value.takeRight(limit)
        case None => bucket.notices.value
      }
    }).toSet

    val histograms = getHistograms(ids, now, true, true, true)
    val notices = NoticeRecord.where(_.id in noticeIds).fetch
    notices.sortBy(_.id.value).reverse.map(n => {
      val nbSet = n.buckets.value.toSet
      val noticeBuckets = buckets.filter(b => nbSet(b.id))
      val noticeBucketRecordHistograms = histograms.filter(h => nbSet(h.bucket))
      MongoOutgoing(n).addBuckets(noticeBuckets, noticeBucketRecordHistograms, now)
    })
  }

  def lastHourHistogram(id: BucketId, now: DateTime): List[Int] = {
    val fullMap = getHistograms(List(id.toString), now, false, false, true)
      .map(_.toEpochMap(now))
      .foldLeft(Map[String,Int]()){_ ++ _}
    val oneHourAgo = now.minusHours(1)
      .withSecondOfMinute(0)
      .withMillisOfSecond(0)
    NumericRange[Long](oneHourAgo.getMillis, oneHourAgo.plusHours(1).getMillis, 60 * 1000L).map(t => {
      fullMap.get(t.toString).getOrElse(0)
    }).toList
  }

  def get(name: String, key: String, now: DateTime) = {
    get(List(BucketId(name, key).toString), None, now)
  }

  def recentKeys(name: String, limit: Option[Int]): List[String] = {
    BucketRecord.where(_._id startsWith name + ":")
      .select(_._id)
      .orderDesc(_.lastSeen)
      .limitOpt(limit)
      .hint(BucketRecord.idIndex)
      .fetch
  }



  def save(incomingId: ObjectId, incoming: Incoming, bucket: BucketId, maxRecent: Int): SaveResult = {
    val n = incoming.n.getOrElse(1)

    val dateTime = new DateTime(incomingId.getTimestamp * 1000L)
    val month = Hash.fieldNameEncode(dateTime.getMonthOfYear)
    val day = Hash.fieldNameEncode(dateTime.getDayOfMonth)
    val hour = Hash.fieldNameEncode(dateTime.getHourOfDay)
    val minute = Hash.fieldNameEncode(dateTime.getMinuteOfHour)
    val bucketKey = bucket.toString

    val existing = Stats.time("bucketActions.save.updateBucket") {
      BucketRecord.where(_._id eqs bucketKey)
        .findAndModify(_.noticeCount inc n)
        .and(_.lastSeen setTo incomingId.getTimestamp * 1000L)
        .and(_.lastVersion setTo incoming.v)
        .and(_.notices push incomingId).upsertOne()
    }

    if (!existing.isDefined) {
      Stats.time("bucketActions.save.upsertBucket") {
        BucketRecord.where(_._id eqs bucketKey)
         .modify(_.firstSeen setTo incomingId.getTimestamp * 1000L)
         .modify(_.firstVersion setTo incoming.v).upsertOne()
      }
    }

    val noticesToRemove = existing.toList.flatMap(e => {
      val len = e.notices.value.length
      if (len >= maxRecent + maxRecent / 2) {
        logger.debug("trimming %d from %s".format(len - maxRecent, bucketKey))
        val toRemove = e.notices.value.take(len - maxRecent)
        Stats.time("bucketActions.save.removeExpiredNotices") {
          BucketRecord.where(_._id eqs bucketKey).modify(_.notices pullAll toRemove).updateOne()
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
      BucketRecordHistogram.where(_._id eqs bucketHour).modify(_.histogram at minute inc n).upsertOne()
      BucketRecordHistogram.where(_._id eqs bucketDay).modify(_.histogram at hour inc n).upsertOne()
      BucketRecordHistogram.where(_._id eqs bucketMonth).modify(_.histogram at day inc n).upsertOne()
    }

    val oldNoticeCount = existing.map(_.noticeCount.value).getOrElse(0)

    SaveResult(
      bucket.count(oldNoticeCount + 1),
      existing.map(b => BucketId(b.id, oldNoticeCount)),
      noticesToRemove)
  }

  def deleteOldHistograms(time: Long, doIt: Boolean = true): Unit = {
    val dateTime = new DateTime(time)
    val oldMonth = dateTime.minusMonths(2)
    val oldDay = dateTime.minusDays(2)
    val oldHour = dateTime.minusHours(2)

    val oldMonthField = Hash.fieldNameEncode(oldMonth.getMonthOfYear)

    val oldDaysPattern = Pattern.compile("^%s[%s]".format(
      Hash.fieldNameEncode(oldDay.getMonthOfYear),
      (0 to oldDay.getDayOfMonth).map(Hash.fieldNameEncode _).mkString("")))

    val oldHoursPattern = Pattern.compile("^%s%s[%s]".format(
      Hash.fieldNameEncode(oldHour.getMonthOfYear),
      Hash.fieldNameEncode(oldHour.getDayOfMonth),
      (0 to oldHour.getHourOfDay).map(Hash.fieldNameEncode _).mkString("")))

    val deleteQueries = List(
      BucketRecordHistogram.where(_._id startsWith oldMonthField),
      BucketRecordHistogram.where(_._id matches oldDaysPattern),
      BucketRecordHistogram.where(_._id matches oldHoursPattern))

    deleteQueries.foreach(q => {
      logger.debug("deleting: %s".format(q))
      if (doIt) {
        Stats.time("bucketActions.save.deleteOldHistograms.delete") {
          q.bulkDelete_!!!()
        }
      }
    })
  }

  def deleteOldBuckets(time: Long, batchSize: Int = 500, doIt: Boolean = true): List[SaveResult] = {
    val dateTime = new DateTime(time)
    val staleDateTime = dateTime.minusMonths(2).minusDays(1)
    val staleTime = staleDateTime.getMillis
    val oldBuckets = BucketRecord.where(_.lastSeen lte staleTime).limit(batchSize).fetch()

    if (!oldBuckets.isEmpty) {
      logger.info("Deleting %d old buckets since %s".format(oldBuckets.length, staleDateTime.toString()))
      val toRemoveResult = oldBuckets.map(b => SaveResult(BucketId(b.id), None, b.notices.value))
      val bucketIds = oldBuckets.map(_.id)
      val q = BucketRecord.where(_._id in bucketIds)
      Stats.time("bucketActions.deleteOldBuckets.delete") {
        logger.debug("deleting: %s".format(q))
        if (doIt) {
          q.bulkDelete_!!!()
        }
      }
      toRemoveResult
    } else {
      Nil
    }
  }
}
