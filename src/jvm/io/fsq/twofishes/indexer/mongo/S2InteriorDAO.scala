// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.
package io.fsq.twofishes.indexer.mongo

import com.mongodb.casbah.Imports._
import io.fsq.twofishes.indexer.mongo.salatfork.SalatDAO
import salat._
import salat.annotations._
import salat.global._

case class S2InteriorIndex(
  @Key("_id") _id: ObjectId,
  cellIds: List[Long]
)

object S2InteriorIndexDAO extends SalatDAO[S2InteriorIndex, String](
  collection = MongoIndexerConnection()("geocoder")("s2_interior_index")) {
  def makeIndexes() {}
}

