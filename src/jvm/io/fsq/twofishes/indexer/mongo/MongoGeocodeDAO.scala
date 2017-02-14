// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.
package io.fsq.twofishes.indexer.mongo

import com.mongodb.casbah.Imports._
import io.fsq.twofishes.indexer.mongo.salatfork.SalatDAO
import io.fsq.twofishes.indexer.util.GeocodeRecord
import salat.global._

object MongoGeocodeDAO extends SalatDAO[GeocodeRecord, ObjectId](
  collection = MongoIndexerConnection()("geocoder")("features")) {
  def makeIndexes() {
    collection.createIndex(DBObject("hasPoly" -> -1))
    collection.createIndex(DBObject("loc" -> "2dsphere", "_woeType" -> -1))
    collection.createIndex(DBObject("polyId" -> 1))
  }
}
