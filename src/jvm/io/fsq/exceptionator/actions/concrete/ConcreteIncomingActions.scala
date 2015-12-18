// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.actions.concrete

import com.twitter.finagle.Service
import com.twitter.ostrich.stats.Stats
import com.twitter.util.{Future, FuturePool}
import io.fsq.exceptionator.actions.{HasBucketActions, HasHistoryActions, HasNoticeActions, IncomingActions}
import io.fsq.exceptionator.filter.{BucketSpec, FilteredIncoming, FilteredSaveService, PreSaveFilter, ProcessedIncoming}
import io.fsq.exceptionator.filter.concrete.FreshBucketFilter
import io.fsq.exceptionator.model.io.BucketId
import io.fsq.exceptionator.util.{Config, Logger, PluginLoader}
import java.util.concurrent.Executors
import scala.collection.JavaConverters._


class FilteredConcreteIncomingActions(service: Service[FilteredIncoming, ProcessedIncoming] with IncomingActions)
  extends Service[FilteredIncoming, ProcessedIncoming] with IncomingActions {

  def bucketFriendlyNames = service.bucketFriendlyNames
  def registerBucket(spec: BucketSpec) {
    service.registerBucket(spec)
  }
  val filters = PluginLoader.defaultConstruct[PreSaveFilter](Config.root.getStringList("incoming.savefilters").asScala)
  service.registerBucket(FreshBucketFilter)
  val filteredService = new FilteredSaveService(service, filters.toList, service)

  def apply(incoming: FilteredIncoming): Future[ProcessedIncoming] = filteredService(incoming)
}

class ConcreteIncomingActions(services: HasBucketActions with HasHistoryActions with HasNoticeActions)
    extends Service[FilteredIncoming, ProcessedIncoming] with IncomingActions with Logger {

  val saveFuturePool = FuturePool(Executors.newFixedThreadPool(10))
  val bucketSpecs = scala.collection.mutable.Map[String, BucketSpec]()
  val incomingFilters = Config.opt(_.getConfigList("incoming.filters").asScala.toList).toList.flatten

  var currentTime: Long = 0
  var lastHistogramTrim: Long = 0

  def registerBucket(spec: BucketSpec) {
    bucketSpecs += spec.name -> spec
  }

  def bucketFriendlyNames = bucketSpecs.toMap.map {
    case (name, builder) => name -> builder.friendlyName
  }

  def apply(incoming: FilteredIncoming): Future[ProcessedIncoming] = {
    saveFuturePool({
      save(incoming)
    })
  }

  def doMaintenance(now: Long) {
    val histogramOldTime = services.historyActions.oldestId.map(_.getMillis).getOrElse(now)
    services.bucketActions.deleteOldHistograms(histogramOldTime, true)

    // Find really stale buckets that haven't been updated for 60 days. And delete them
    Stats.time("incomingActions.deleteOldBuckets") {
      val toRemove = services.bucketActions.deleteOldBuckets(now)
      toRemove.foreach(tr => tr.noticesToRemove.foreach(n =>
        services.noticeActions.removeBucket(n, tr.bucket)
      ))
    }
  }

  def save(incoming: FilteredIncoming): ProcessedIncoming = {

    val tags = incoming.tags
    val kw = incoming.keywords
    val buckets = incoming.buckets

    val notice = services.noticeActions.save(incoming.incoming, tags, kw, buckets)
    val incomingId = notice.id.value

    val historyId = services.historyActions.save(notice)

    // Increment /create buckets
    val results = buckets.map(bucket => {
      val max = bucketSpecs(bucket.name).maxRecent
      services.bucketActions.save(incomingId, incoming.incoming, bucket, max)
    })

    // As long as nothing that already exists invalidates freshness, we call it fresh
    val finalBuckets = {
      if (!results.exists(r => r.oldResult.isDefined &&
          bucketSpecs(r.bucket.name).invalidatesFreshness)) {

        val freshKey = BucketId(FreshBucketFilter.name, FreshBucketFilter.key(incoming).get)

        val res = services.bucketActions.save(
          incomingId,
          incoming.incoming,
          freshKey,
          FreshBucketFilter.maxRecent)

        Stats.time("incomingActions.add") {
          services.noticeActions.addBucket(incomingId, freshKey)
        }
        buckets + freshKey
      } else {
        buckets
      }
    }

    val remove = results.flatMap(r => r.noticesToRemove.map(_ -> r.bucket)).toList


    // Fix up old notices that have been kicked out
    Stats.time("incomingActions.remove") {
      remove.foreach(bucketRemoval => services.noticeActions.removeBucket(bucketRemoval._1, bucketRemoval._2))
    }

    // A bit racy, but only approximation is needed.  Want to trim histograms
    // about every hour
    val now = incomingId.getTimestamp * 1000L
    if (now > currentTime) {
      currentTime = now
      if (currentTime - lastHistogramTrim > (60L/*mins*/ * 60/*secs*/ * 1000)) {
        lastHistogramTrim = now
        doMaintenance(now)
      }
    }

    ProcessedIncoming(Some(incomingId), incoming.incoming, tags, kw, finalBuckets)
  }
}
