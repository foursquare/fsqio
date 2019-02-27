// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.actions.concrete

import com.twitter.conversions.time._
import com.twitter.ostrich.stats.Stats
import com.twitter.util.ScheduledThreadPoolTimer
import io.fsq.common.logging.Logger
import io.fsq.common.scala.Identity._
import io.fsq.common.scala.Lists.Implicits._
import io.fsq.exceptionator.actions.{HasBucketActions, HistoryActions}
import io.fsq.exceptionator.model.{MongoOutgoing, RichBucketRecord, RichHistoryRecord, RichNoticeRecord}
import io.fsq.exceptionator.model.gen.{BucketRecord, HistoryRecord}
import io.fsq.exceptionator.model.io.{BucketId, Outgoing}
import io.fsq.exceptionator.mongo.{ExceptionatorMongoService, HasExceptionatorMongoService}
import io.fsq.exceptionator.util.{Config, ReservoirSampler}
import io.fsq.spindle.rogue.{SpindleQuery => Q}
import io.fsq.spindle.rogue.SpindleRogue._
import java.util.concurrent.ConcurrentHashMap
import org.joda.time.DateTime
import scala.collection.JavaConverters.mapAsScalaConcurrentMapConverter
import scala.language.postfixOps

class ConcreteHistoryActions(
  services: HasBucketActions with HasExceptionatorMongoService
) extends HistoryActions
  with Logger {
  val flushPeriod = Config.opt(_.getInt("history.flushPeriod")).getOrElse(60)
  val sampleRate = Config.opt(_.getInt("history.sampleRate")).getOrElse(50)
  val samplers = (new ConcurrentHashMap[DateTime, ReservoirSampler[RichNoticeRecord]]).asScala
  val timer = new ScheduledThreadPoolTimer(makeDaemons = true)
  def executor: ExceptionatorMongoService.Executor = services.exceptionatorMongo.executor

  timer.schedule(flushPeriod seconds, flushPeriod seconds) {
    try {
      Stats.time("historyActions.flushHistory") {
        flushHistory()
      }
    } catch {
      case t: Throwable => logger.debug("Error flushing history", t)
    }
  }

  // Write history to mongo
  def flushHistory(): Unit = {
    for {
      historyId <- samplers.keys
      sampler <- samplers.remove(historyId)
    } {
      val merged = executor
        .fetchOne(
          Q(HistoryRecord)
            .where(_.id eqs historyId)
        )
        .unwrap
        .filter(_.sampleRate =? sampler.size)
        .map(existing => {
          logger.debug(s"Writing histories for ${historyId}")
          Stats.time("historyActions.flushMerge") {
            val notices = existing.notices.map(RichNoticeRecord(_))
            val existingState = ReservoirSampler.State(notices, existing.totalSampled)
            val existingSampler = ReservoirSampler(existing.sampleRate, existingState)
            val merged = ReservoirSampler.merge(existingSampler, sampler)
            val state = merged.state
            val sorted = state.samples.sortBy(_.id).reverse
            val buckets = sorted.flatMap(_.buckets).distinct
            val earliestExpirationOpt: Option[DateTime] = sorted
              .flatMap(_.expireAtOption)
              .minByOption(_.getMillis)

            val builder = existing.toBuilder

            builder
              .notices(sorted.toVector)
              .buckets(buckets.toVector)
              .totalSampled(state.sampled)
              .earliestExpiration(earliestExpirationOpt)

            executor.save(builder.result())
          }
        })

      if (merged.isEmpty) {
        logger.debug(s"Writing new history for ${historyId}")
        Stats.time("historyActions.flushNew") {
          val state = sampler.state
          val sorted = state.samples.sortBy(_.id).reverse
          val buckets = sorted.flatMap(_.buckets).distinct
          val earliestExpirationOpt = sorted.flatMap(_.expireAtOption).minByOption(_.getMillis)
          val builder = HistoryRecord.newBuilder.id(historyId)

          builder
            .notices(sorted.toList)
            .buckets(buckets.toList)
            .sampleRate(sampleRate)
            .totalSampled(state.sampled)
            .earliestExpiration(earliestExpirationOpt)

          executor.save(builder.result())
        }
      }
    }
  }

  def get(bucketName: String, time: DateTime, limit: Int): Seq[Outgoing] = {
    val notices = getGroupNotices(bucketName, time, limit)
    val ids = notices.flatMap(_.buckets.filter(_.startsWith(bucketName + ":")))
    processNotices(ids, notices, time)
  }

  def get(bucketName: String, bucketKey: String, time: DateTime, limit: Int): Seq[Outgoing] = {
    get(List(BucketId(bucketName, bucketKey).toString), time, limit)
  }

  def get(ids: Seq[String], time: DateTime, limit: Int): Seq[Outgoing] = {
    val notices = getNotices(ids, time, limit)
    processNotices(ids, notices, time)
  }

  def getNotices(buckets: Seq[String], time: DateTime, limit: Int): Seq[RichNoticeRecord] = {
    val historyId = RichHistoryRecord.idForTime(time)
    val historyOpt: Option[HistoryRecord] = executor
      .fetch(
        Q(HistoryRecord)
          .where(_.buckets in buckets)
          .and(_.id lte historyId)
          .orderDesc(_.id)
          .limit(1)
      )
      .unwrap
      .headOption

    historyOpt
      .map(thriftHistory => {
        // use a view to avoid creating an intermediate collection at each step
        val history = RichHistoryRecord(thriftHistory)

        history.notices.view
          .map(RichNoticeRecord(_))
          .dropWhile(_.createDateTime.isAfter(time))
          .filter(_.buckets.exists(buckets.contains(_)))
          .take(limit)
          .toVector
      })
      .getOrElse(Nil)
  }

  def getGroupNotices(name: String, time: DateTime, limit: Int): Seq[RichNoticeRecord] = {
    val historyId = RichHistoryRecord.idForTime(time)
    val historyOpt: Option[HistoryRecord] = executor
      .fetchOne(
        Q(HistoryRecord)
          .where(_.buckets contains name + ":")
          .and(_.id lte historyId)
          .orderDesc(_.id)
      )
      .unwrap

    historyOpt.view
      .flatMap(thriftHistory => {
        val history = RichHistoryRecord(thriftHistory)
        // use a view to avoid creating an intermediate collection at each step
        history.notices.view
          .map(RichNoticeRecord(_))
          .dropWhile(_.createDateTime.isAfter(time))
          .filter(_.buckets.exists(_.startsWith(name + ":")))
          .take(limit)
      })
      .take(limit)
      .toVector
  }

  def oldestId: Option[DateTime] = {
    executor
      .fetchOne(
        Q(HistoryRecord)
          .select(_.id)
          .orderAsc(_.id)
      )
      .unwrap
      .headOption
      .map(new DateTime(_))
  }

  def processNotices(ids: Seq[String], notices: Seq[RichNoticeRecord], time: DateTime): Seq[Outgoing] = {
    val buckets = executor
      .fetch(
        Q(BucketRecord)
          .where(_.id in ids)
      )
      .unwrap
    val histograms = services.bucketActions.getHistograms(ids, time, true, true, true)
    notices.map(n => {
      val nbSet = n.buckets.toSet
      val noticeBuckets = buckets.view
        .filter(b => nbSet(b.id))
        .map(RichBucketRecord(_))
        .toVector
      val noticeBucketRecordHistograms = histograms.filter(h => nbSet(h.bucket))
      MongoOutgoing(n).addBuckets(noticeBuckets, noticeBucketRecordHistograms, time)
    })
  }

  def removeExpiredNotices(now: DateTime): Unit = {
    // NOTE(jacob): The number of these returned is dependent upon a few factors, but we
    //    will fetch a max of incoming.maintenanceWindow / history.flushPeriod records.
    val historyRecords = executor
      .fetch(
        Q(HistoryRecord)
          .where(_.earliestExpiration before now)
      )
      .unwrap

    // TODO(jacob): Once exceptionator is ported to the new mongo client this should be a
    //    single bulk update.
    for (record <- historyRecords) {
      val noticeRecords = record.notices.map(RichNoticeRecord(_))
      val validNotices = noticeRecords.filter(notice => {
        notice.expireAtOption.map(_.isAfter(now)).getOrElse(true)
      })
      val updatedBuckets = validNotices.flatMap(_.buckets).distinct
      val newEarliestExpirationOpt = validNotices
        .flatMap(_.expireAtOption)
        .minByOption(_.getMillis)

      val builder = record.toBuilder

      builder
        .notices(validNotices)
        .buckets(updatedBuckets)
        .earliestExpiration(newEarliestExpirationOpt)

      executor.save(builder.result())
    }
  }

  // Save a notice to its HistoryRecord, using reservoir sampling
  def save(notice: RichNoticeRecord): DateTime = {
    // use the client-provided date if available, otherwise record creation time
    val dateTime: DateTime = notice.noticeOrThrow.dateOption
      .map(date => {
        new DateTime(date)
      })
      .getOrElse(notice.createDateTime)

    val historyId: DateTime = RichHistoryRecord.idForTime(dateTime)
    val sampler = samplers.getOrElseUpdate(historyId, new ReservoirSampler[RichNoticeRecord](sampleRate))
    sampler.update(notice)
    historyId
  }
}
