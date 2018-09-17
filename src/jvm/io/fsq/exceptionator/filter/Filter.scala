// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.filter

import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.Future
import io.fsq.exceptionator.model.io.{BucketId, RichIncoming}
import org.bson.types.ObjectId

object FilteredIncoming {
  def apply(incoming: RichIncoming): FilteredIncoming = FilteredIncoming(incoming, Set.empty, Set.empty, Set.empty)
}

case class FilteredIncoming(incoming: RichIncoming, tags: Set[String], keywords: Set[String], buckets: Set[BucketId])

case class ProcessedIncoming(
  id: Option[ObjectId],
  incoming: RichIncoming,
  tags: Set[String],
  keywords: Set[String],
  buckets: Set[BucketId]
)

trait BucketSpec {
  def name: String
  def friendlyName: String
  def maxRecent: Int
  def invalidatesFreshness: Boolean
}

trait Registry {
  def registerBucket(spec: BucketSpec): Unit
}

abstract class PreSaveFilter extends SimpleFilter[FilteredIncoming, ProcessedIncoming] {
  def register(registry: Registry): Unit
}

/* Builds a up a service in reverse order where each filter on the service
 * yields a new service
 */
class FilteredSaveService(
  service: Service[FilteredIncoming, ProcessedIncoming],
  filters: Seq[PreSaveFilter],
  registry: Registry
) extends Service[FilteredIncoming, ProcessedIncoming] {

  val filteredService = filters.foldRight(service)((filter, service) => {
    filter.register(registry)
    filter andThen service
  })

  def apply(incoming: FilteredIncoming): Future[ProcessedIncoming] = filteredService(incoming)
}
