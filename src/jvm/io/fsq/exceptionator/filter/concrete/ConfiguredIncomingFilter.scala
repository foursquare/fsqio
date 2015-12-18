// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.filter.concrete

import com.twitter.finagle.Service
import com.twitter.util.Future
import io.fsq.exceptionator.filter.{FilteredIncoming, IncomingFilter, PreSaveFilter, ProcessedIncoming, Registry}
import io.fsq.exceptionator.util.Config
import scala.collection.JavaConverters._

class ConfiguredIncomingFilter extends PreSaveFilter {
  def register(registry: Registry) {}

  val incomingFilters = Config.opt(_.getConfigList("incoming.filters").asScala.toList).toList.flatten

  def apply(incoming: FilteredIncoming, service: Service[FilteredIncoming, ProcessedIncoming]) = {
    val filterRes = IncomingFilter.checkFilters(incoming.incoming, incomingFilters)
    val filteredIncoming = filterRes.map(_.incoming).getOrElse(incoming.incoming)
    if (filterRes.exists(_.allowed == Some(false))) {
      Future.value(ProcessedIncoming(None, incoming.incoming, incoming.tags, incoming.keywords, incoming.buckets))
    } else {
      service.apply(incoming.copy(incoming=filteredIncoming))
    }
  }
}
