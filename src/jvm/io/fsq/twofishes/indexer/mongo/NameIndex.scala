// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.twofishes.indexer.mongo

import io.fsq.rogue.index.{Asc, Desc, MongoIndex}
import io.fsq.twofishes.gen.YahooWoeType
import io.fsq.twofishes.model.gen.{ThriftNameIndex, ThriftNameIndexMeta, ThriftNameIndexProxy}
import io.fsq.twofishes.util.StoredFeatureId
import org.bson.types.ObjectId

object NameIndex {
  def apply(
    name: String,
    fid: Long,
    cc: String,
    pop: Int,
    woeType: Int,
    flags: Int,
    lang: String,
    excludeFromPrefixIndex: Boolean,
    id: ObjectId
  ): NameIndex = {
    val thriftModel = ThriftNameIndex.newBuilder
      .name(name)
      .fid(fid)
      .cc(cc)
      .pop(pop)
      .woeType(YahooWoeType.findById(woeType).getOrElse(throw new Exception(s"Unknown YahooWoeType ID: ${woeType}")))
      .flags(flags)
      .lang(lang)
      .excludeFromPrefixIndex(excludeFromPrefixIndex)
      .id(id)
      .result()
    new NameIndex(thriftModel)
  }

  def makeIndexes(executor: IndexerQueryExecutor.ExecutorT): Unit = {
    val builder = MongoIndex.builder[ThriftNameIndexMeta](ThriftNameIndex)
    executor.createIndexes(ThriftNameIndex)(
      builder.index(_.name, Asc, _.excludeFromPrefixIndex, Asc, _.pop, Desc),
      builder.index(_.fid, Asc, _.lang, Asc, _.name, Asc)
    )
  }
}

class NameIndex(override val underlying: ThriftNameIndex) extends ThriftNameIndexProxy {
  def fidAsFeatureId =
    StoredFeatureId.fromLong(fid).getOrElse(throw new RuntimeException("can't convert %d to a feature id".format(fid)))
}
