
// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.filter

import com.twitter.finagle.Service

abstract class TagFilter extends PreSaveFilter {
  def register(registry: Registry) {}

  def apply(incoming: FilteredIncoming, service: Service[FilteredIncoming, ProcessedIncoming]) = {
    val newIncoming = incoming.copy(tags=(incoming.tags ++ tags(incoming)))
    service(newIncoming)
  }

  def tags(incoming: FilteredIncoming): Set[String]
}
