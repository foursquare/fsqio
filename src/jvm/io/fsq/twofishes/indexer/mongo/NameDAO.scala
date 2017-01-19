// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.
package io.fsq.twofishes.indexer.mongo

import com.mongodb.casbah.Imports._
import io.fsq.twofishes.util.StoredFeatureId
import salat._
import salat.annotations._
import salat.dao._
import salat.global._

case class NameIndex(
  name: String,
  fid: Long,
  cc: String,
  pop: Int,
  woeType: Int,
  flags: Int,
  lang: String,
  excludeFromPrefixIndex: Boolean,
  @Key("_id") _id: ObjectId
) {

  def fidAsFeatureId = StoredFeatureId.fromLong(fid).getOrElse(
    throw new RuntimeException("can't convert %d to a feature id".format(fid)))
}

object NameIndexDAO extends SalatDAO[NameIndex, String](
  collection = MongoIndexerConnection()("geocoder")("name_index")) {
  def makeIndexes() {
    collection.createIndex(DBObject("name" -> 1, "excludeFromPrefixIndex" -> 1, "pop" -> -1))
    collection.createIndex(DBObject("fid" -> 1, "lang" -> 1, "name" -> 1))
  }
}
