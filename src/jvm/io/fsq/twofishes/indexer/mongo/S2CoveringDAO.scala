// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.
package io.fsq.twofishes.indexer.mongo

import com.mongodb.casbah.Imports._
import salat._
import salat.annotations._
import salat.dao._
import salat.global._

case class S2CoveringIndex(
  @Key("_id") _id: ObjectId,
  cellIds: List[Long]
)

object S2CoveringIndexDAO extends SalatDAO[S2CoveringIndex, String](
  collection = MongoIndexerConnection()("geocoder")("s2_covering_index")) {
  def makeIndexes() {}
}
