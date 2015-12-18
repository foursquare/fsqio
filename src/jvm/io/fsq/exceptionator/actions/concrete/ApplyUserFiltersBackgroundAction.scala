// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.actions.concrete

import com.twitter.util.{Future, FuturePool}
import io.fsq.exceptionator.actions.{HasBucketActions, HasNoticeActions, HasUserFilterActions}
import io.fsq.exceptionator.filter.ProcessedIncoming
import io.fsq.exceptionator.model.{FilterType, TriggerType, UserFilterRecord}
import io.fsq.exceptionator.model.io.BucketId
import io.fsq.exceptionator.util.PollingCache
import io.fsq.rogue.lift.LiftRogue._
import java.util.concurrent.Executors
import org.joda.time.DateTime
import scala.collection.GenSet
import scala.collection.JavaConverters._

class ApplyUserFiltersBackgroundAction (
  services: HasBucketActions
    with HasNoticeActions
    with HasUserFilterActions) extends EmailExceptionBackgroundAction {

  val mongoCmdFuturePool = FuturePool(Executors.newFixedThreadPool(10))

  val filters = new PollingCache[List[UserFilterRecord]](() => {
    val filterRecords = UserFilterRecord.fetch(2000)
    filterRecords
  }, 10)

  def hasAll(
    filterType: FilterType.Value,
    criteriaOpt: Option[List[String]],
    processedIncoming: ProcessedIncoming): Boolean = {

    import FilterType._
    val valuesOpt: Option[GenSet[String]] = filterType match {
      case NullFilter => None
      case KeywordFilter => Some(processedIncoming.keywords)
      case BucketFilter => Some(processedIncoming.buckets.map(_.toString))
    }
    (for {
      values <- valuesOpt
      criteria <- criteriaOpt
    } yield {
      val criteriaSet = criteria.toSet
      (criteriaSet & values) == criteriaSet
    }).exists(_ == true)
  }

  def applyFilters(processedIncoming: ProcessedIncoming): Future[Seq[(UserFilterRecord, BucketId)]] = {
    val applicable = filters.get
      // has criteria
      .filter(f => hasAll(f.filterType.value, f.criteria.valueBox, processedIncoming))

      // success!
      .flatMap(f => {
        processedIncoming.id.map(id => {
          // Update last matched time:
          mongoCmdFuturePool({
            UserFilterRecord.where(_.id eqs f.id.value).modify(_.lastMatched setTo id.getTimestamp * 1000L).updateOne()
          })

          // update local cache (racy, but I think this is acceptable)
          f.lastMatched(id.getTimestamp * 1000L)

          // Add the notice to the filter's bucket
          val bucket = BucketId("uf", f.id.toString)

          // Add the bucket to the notice
          mongoCmdFuturePool({
            services.noticeActions.addBucket(id, bucket)
          })


          val res: Future[(UserFilterRecord, BucketId)] = mongoCmdFuturePool({
            val saveRes = services.bucketActions.save(
              id,
              processedIncoming.incoming,
              bucket,
              60)
            // Delete old notifications
            saveRes.noticesToRemove.map(_ -> saveRes.bucket).foreach(bucketRemoval =>
              services.noticeActions.removeBucket(bucketRemoval._1, bucketRemoval._2))

            (f, saveRes.bucket)
          })

          res
        })
      })
      Future.collect(applicable)

  }

  def meetsTriggerCriteria(
    processedIncoming: ProcessedIncoming,
    filter: UserFilterRecord,
    filterBucket: BucketId): Future[Boolean] = {

    logger.info("trigger %s %s".format(filter, filterBucket))

    // not muted

    import TriggerType._
    val isTriggered = filter.triggerType.value match {
      case NullTrigger => throw new Exception("%s has null trigger type".format(filter))
      case AlwaysTrigger => Future.value(true)
      case NeverTrigger => Future.value(false)
      case PowerOfTwoTrigger => Future.value(filterBucket.count.exists(c => (c & (c - 1)) == 0))
      case PeriodicTrigger =>
        Future.value(filter.triggerPeriod.valueBox.map((triggerPeriod: Int) => {
          processedIncoming.id.exists(_.getTimestamp * 1000L > filter.lastMatched.value + triggerPeriod * 60L * 1000L)
        }).getOrElse({
          logger.error("Expected a triggerPeriod on %s".format(filter))
          false
        }))
      case ThresholdTrigger =>
        // Get a list of 60 ints (past hour) head is 60 mins ago, tail is now
        mongoCmdFuturePool(
          services.bucketActions.lastHourHistogram(
            filterBucket,
            new DateTime(processedIncoming.id.get.getTimestamp * 1000L))
        ).map(h => {
          (for {
            triggerPeriod <- filter.triggerPeriod.valueBox
            thresholdLevel <- filter.thresholdLevel.valueBox
            thresholdCount <- filter.thresholdCount.valueBox
          } yield {
            // Added a +1 to fudge things a bit when muting is taken into consideration
            val isTriggered = h.reverse.take(triggerPeriod + 1).filter(_ >= thresholdLevel).length >= thresholdCount

            // If triggered, mute for triggerPeriod time, else just mute
            // for minute granularity time (1 minute) because nothing is going to change between now and then.
            // Want to avoid bringing back the histograms and analyzing them all of the time.
            val mutePeriod = if (isTriggered) {
              triggerPeriod
            } else {
              1
            }
            triggerMute(filter, (processedIncoming.id.get.getTimestamp + mutePeriod * 60) * 1000L)

            isTriggered
          }).exists(_ == true)
        })
      case unknownType =>
        logger.info("No TriggerType match for " + unknownType)
        Future.value(false)
    }
    isTriggered
  }

  def triggerMute(filter: UserFilterRecord, until: Long) {
    // If there isn't a mute further into the future, then set
    if (! filter.muteUntil.valueBox.exists(_ > until)) {
      logger.info("automated mute: %s until %s".format(filter.id.toString, new DateTime(until).toString()))
      filter.muteUntil(until)
      mongoCmdFuturePool({
        UserFilterRecord.where(_.id eqs filter.id.value).modify(_.muteUntil setTo Some(until)).updateOne()
      })
    }
  }

  def shouldEmail(processedIncoming: ProcessedIncoming): Future[Option[SendInfo]] = {
    applyFilters(processedIncoming).flatMap(applied => {
      Future.collect(applied
        // filter mute
        .filter { case (f, b) =>
           f.muteUntil.valueBox.map(m => processedIncoming.id.exists(_.getTimestamp * 1000L > m)).getOrElse(true)
        }.map {
        case (f, b) => meetsTriggerCriteria(processedIncoming, f, b).map((isTriggered: Boolean) => {
          logger.info("isTriggered: %s %s".format(isTriggered, f))
          if (isTriggered) {
            Some(f, b)
          } else {
            None
          }
        })
      }).map(_.flatten.unzip3 {
        case (filter, filterBucket) => {
          val filterLinkMessage = "Generated by filter:  http://%s/filters/%s (owner: %s)".format(
            prettyHost,
            filter.id,
            filter.userId.value)


          val filterBucketMessage = "Matched exceptions: http://%s/notices/uf/%s %s".format(
            prettyHost,
            filter.id,
            filterBucket.count.map("now at %d".format(_)).getOrElse(""))

          val res = (
            "%s\n%s".format(filterLinkMessage, filterBucketMessage),
            filter.userId.value,
            filter.cc.value
          )
          res
        }
      }).map(_ match {
        case (Nil, Nil, Nil) => None
        case (infoList, toList, ccList) =>
          Some(SendInfo(
            infoList.mkString("\n"),
            toList.toSet.toList,
            ccList.flatten.toSet.toList))
      })
    })
  }
}
