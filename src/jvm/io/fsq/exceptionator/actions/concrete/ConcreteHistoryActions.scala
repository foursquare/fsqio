// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.actions.concrete

import com.twitter.conversions.time._
import com.twitter.ostrich.stats.Stats
import com.twitter.util.ScheduledThreadPoolTimer
import io.fsq.exceptionator.actions.{HasBucketActions, HistoryActions, IndexActions}
import io.fsq.exceptionator.model.{BucketRecord, HistoryRecord, MongoOutgoing, NoticeRecord}
import io.fsq.exceptionator.model.io.{BucketId, Outgoing}
import io.fsq.exceptionator.util.{Config, Logger, ReservoirSampler}
import io.fsq.rogue.lift.LiftRogue._
import java.util.concurrent.ConcurrentHashMap
import net.liftweb.json.{JField, JInt, JObject}
import org.joda.time.DateTime
import scala.collection.JavaConverters.mapAsScalaConcurrentMapConverter
import scala.language.postfixOps


class ConcreteHistoryActions(services: HasBucketActions) extends HistoryActions with IndexActions with Logger {
  val flushPeriod = Config.opt(_.getInt("history.flushPeriod")).getOrElse(60)
  val sampleRate = Config.opt(_.getInt("history.sampleRate")).getOrElse(50)
  val samplers = (new ConcurrentHashMap[DateTime, ReservoirSampler[NoticeRecord]]).asScala
  val timer = new ScheduledThreadPoolTimer(makeDaemons=true)

  timer.schedule(flushPeriod seconds, flushPeriod seconds) {
    try {
      Stats.time("historyActions.flushHistory") {
        flushHistory()
      }
    } catch {
      case t: Throwable => logger.debug("Error flushing history", t)
    }
  }

  def ensureIndexes {
    Vector(HistoryRecord).foreach(metaRecord => {
      metaRecord.mongoIndexList.foreach(i =>
        metaRecord.createIndex(JObject(i.asListMap.map(fld => JField(fld._1, JInt(fld._2.toString.toInt))).toList)))
    })
  }

  // Write history to mongo
  def flushHistory(): Unit = {
    for {
      historyId <- samplers.keys
      sampler <- samplers.remove(historyId)
    } {
      val saved = HistoryRecord.where(_.id eqs historyId).get()
        .filter(_.sampleRate.value == sampler.size)
        .map(existing => {
          logger.debug(s"Merging histories for ${historyId}")
          Stats.time("historyActions.flushMerge") {
            val existingState = ReservoirSampler.State(existing.notices.value, existing.totalSampled.value)
            val existingSampler = ReservoirSampler(existing.sampleRate.value, existingState)
            val merged = ReservoirSampler.merge(existingSampler, sampler)
            val state = merged.state
            val sorted = state.samples.sortBy(_.id.value).reverse
            val buckets = sorted.flatMap(_.buckets.value).distinct
            existing
              .notices(sorted.toList)
              .buckets(buckets.toList)
              .totalSampled(state.sampled)
              .save(true)
          }
        }).getOrElse {
          logger.debug(s"Writing new history for ${historyId}")
          Stats.time("historyActions.flushNew") {
            val state = sampler.state
            val sorted = state.samples.sortBy(_.id.value).reverse
            val buckets = sorted.flatMap(_.buckets.value).distinct
            HistoryRecord.createRecord
              .id(historyId)
              .notices(sorted.toList)
              .buckets(buckets.toList)
              .sampleRate(sampleRate)
              .totalSampled(state.sampled)
              .save(true)
          }
        }
    }
  }

  def get(bucketName: String, time: DateTime, limit: Int): List[Outgoing] = {
    val notices = getGroupNotices(bucketName, time, limit)
    val ids = notices.flatMap(_.buckets.value.filter(_.startsWith(bucketName + ":")))
    processNotices(ids, notices, time)
  }

  def get(bucketName: String, bucketKey: String, time: DateTime, limit: Int): List[Outgoing] = {
    get(List(BucketId(bucketName, bucketKey).toString), time, limit)
  }

  def get(ids: List[String], time: DateTime, limit: Int): List[Outgoing] = {
    val notices = getNotices(ids, time, limit)
    processNotices(ids, notices, time)
  }

  def getNotices(buckets: List[String], time: DateTime, limit: Int): List[NoticeRecord] = {
    val historyId = HistoryRecord.idForTime(time)
    val historyOpt = HistoryRecord
      .where(_.buckets in buckets)
      .and(_.id lte historyId)
      .orderDesc(_.id)
      .hint(HistoryRecord.bucketIdIndex)
      .get()

    historyOpt.map { history =>
      // use a view to avoid creating an intermediate collection at each step
      val notices = history.notices.value.view
        .dropWhile(_.createDateTime.isAfter(time))
        .filter(_.buckets.value.exists(buckets.contains(_)))
        .take(limit)
        .toList
      if (notices.size < limit) {
        // recurse and look at previous records to flush out our list
        notices ++ getNotices(buckets, history.id.dateTimeValue.minusMillis(1), limit - notices.size)
      } else {
        notices
      }
    }.getOrElse(Nil)
  }

  def getGroupNotices(name: String, time: DateTime, limit: Int): List[NoticeRecord] = {
    val historyId = HistoryRecord.idForTime(time)
    val historyOpt = HistoryRecord
      .where(_.buckets startsWith name + ":")
      .and(_.id lte historyId)
      .orderDesc(_.id)
      .hint(HistoryRecord.bucketIdIndex)
      .get()

    historyOpt.map { history =>
      // use a view to avoid creating an intermediate collection at each step
      val notices = history.notices.value.view
        .dropWhile(_.createDateTime.isAfter(time))
        .filter(_.buckets.value.exists(_.startsWith(name + ":")))
        .take(limit)
        .toList
      if (notices.size < limit) {
        // recurse and look at previous records to flush out our list
        notices ++ getGroupNotices(name, history.id.dateTimeValue.minusMillis(1), limit - notices.size)
      } else {
        notices
      }
    }.getOrElse(Nil)
  }

  def oldestId: Option[DateTime] = HistoryRecord.select(_.id).orderAsc(_.id).get().map(new DateTime(_))

  def processNotices(ids: List[String], notices: List[NoticeRecord], time: DateTime): List[Outgoing] = {
    val buckets = BucketRecord.where(_._id in ids).fetch
    val histograms = services.bucketActions.getHistograms(ids, time, true, true, true)
    notices.map(n => {
      val nbSet = n.buckets.value.toSet
      val noticeBuckets = buckets.filter(b => nbSet(b.id))
      val noticeBucketRecordHistograms = histograms.filter(h => nbSet(h.bucket))
      MongoOutgoing(n).addBuckets(noticeBuckets, noticeBucketRecordHistograms, time)
    })
  }

  // Save a notice to its HistoryRecord, using reservoir sampling
  def save(notice: NoticeRecord): DateTime = {
    // use the client-provided date if available, otherwise record creation time
    val dateTime = notice.notice.value.d.map(new DateTime(_)).getOrElse(notice.createDateTime)
    val historyId = HistoryRecord.idForTime(dateTime)
    val sampler = samplers.getOrElseUpdate(historyId, new ReservoirSampler[NoticeRecord](sampleRate))
    sampler.update(notice)
    historyId
  }
}
