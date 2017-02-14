// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.
package io.fsq.twofishes.indexer.mongo

import com.mongodb.casbah.Imports._
import io.fsq.twofishes.gen.GeocodePoint
import io.fsq.twofishes.indexer.mongo.salatfork.SalatDAO
import salat._
import salat.annotations._
import salat.global._

case class RevGeoIndex(
  cellid: Long,
  polyId: ObjectId,
  full: Boolean,
  geom: Option[Array[Byte]],
  point: Option[(Double, Double)] = None
) {
  def getGeocodePoint: Option[GeocodePoint] = {
    point.map({case (lat, lng) => {
      GeocodePoint.newBuilder.lat(lat).lng(lng).result
	}})
  }
}

object RevGeoIndexDAO extends SalatDAO[RevGeoIndex, String](
  collection = MongoIndexerConnection()("geocoder")("revgeo_index")) {
  def makeIndexes() {
    collection.createIndex(DBObject("cellid" -> -1))
  }
}
