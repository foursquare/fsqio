// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.
package io.fsq.twofishes.indexer.mongo

import com.mongodb.casbah.Imports._
import io.fsq.twofishes.indexer.mongo.salatfork.SalatDAO
import salat._
import salat.annotations._
import salat.global._

case class PolygonIndex(
  @Key("_id") _id: ObjectId,
  polygon: Array[Byte],
  source: String
)

object PolygonIndexDAO extends SalatDAO[PolygonIndex, String](
  collection = MongoIndexerConnection()("geocoder")("polygon_index")) {
  def makeIndexes() {}
}
