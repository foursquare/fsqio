// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.filter

import com.twitter.finagle.Service
import io.fsq.exceptionator.model.io.BucketId

abstract class BucketFilter extends PreSaveFilter with BucketSpec {
  def name: String
  def friendlyName: String
  def maxRecent = 20
  def invalidatesFreshness = true

  def register(registry: Registry) {
    registry.registerBucket(this)
  }

  def apply(incoming: FilteredIncoming, service: Service[FilteredIncoming, ProcessedIncoming]) = {
    val newIncoming = key(incoming) match {
      case Some(k) => incoming.copy(buckets=(incoming.buckets + BucketId(name, k)))
      case _ => incoming
    }
    service(newIncoming)
  }

  def key(incoming: FilteredIncoming): Option[String]
}
