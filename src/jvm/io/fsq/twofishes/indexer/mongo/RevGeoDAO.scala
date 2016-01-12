// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.
package io.fsq.twofishes.indexer.mongo

import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.annotations._
import com.novus.salat.dao._
import com.novus.salat.global._
import io.fsq.twofishes.gen.GeocodePoint

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
    collection.ensureIndex(DBObject("cellid" -> -1))
  }
}
