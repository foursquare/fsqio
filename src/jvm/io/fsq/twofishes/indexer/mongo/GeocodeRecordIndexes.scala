// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.twofishes.indexer.mongo

import io.fsq.rogue.index.{Asc, Desc, MongoIndex}
import io.fsq.twofishes.model.gen.{ThriftGeocodeRecord, ThriftGeocodeRecordMeta}

object GeocodeRecordIndexes {
  def makeIndexes(executor: IndexerQueryExecutor.ExecutorT): Unit = {
    val builder = MongoIndex.builder[ThriftGeocodeRecordMeta](ThriftGeocodeRecord)
    executor.createIndexes(
      builder.index(_.hasPoly, Desc),
      builder.index(_.polyId, Asc)
    )
  }
}
