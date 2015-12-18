// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.actions

import com.twitter.util.Future
import io.fsq.exceptionator.filter.{BucketSpec, FilteredIncoming, ProcessedIncoming, Registry}

trait HasIncomingActions {
  def incomingActions: IncomingActions
}

trait IncomingActions extends Registry {
  def registerBucket(bucket: BucketSpec)
  def bucketFriendlyNames: Map[String, String]
  def apply(incoming: FilteredIncoming): Future[ProcessedIncoming]
}
