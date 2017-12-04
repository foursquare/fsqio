// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.twofishes.indexer.mongo

import io.fsq.rogue.index.{Asc, Desc, MongoIndex}
import io.fsq.twofishes.model.gen.{ThriftGeocodeRecord, ThriftGeocodeRecordMeta}

/*
 * Most of the makeIndexes methods are on the model's companion objects, so you'd think that's where
 * this would be right? Unfortunately GeocodeRecord lives in indexer.util, which depends on mongo,
 * so having mongo depend on indexer.util would create a dep cycle. But then GeocodeRecord can't
 * be moved into util because deps. So, we put this here.
 */
object GeocodeRecordIndexes {
  def makeIndexes(executor: IndexerQueryExecutor.ExecutorT): Unit = {
    val builder = MongoIndex.builder[ThriftGeocodeRecordMeta](ThriftGeocodeRecord)
    executor.createIndexes(ThriftGeocodeRecord)(
      builder.index(_.hasPoly, Desc),
      builder.index(_.polyId, Asc)
    )
  }
}
