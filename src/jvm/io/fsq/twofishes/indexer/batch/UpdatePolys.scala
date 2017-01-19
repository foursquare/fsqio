//  Copyright 2012 Foursquare Labs Inc. All Rights Reserved
package io.fsq.twofishes.indexer.batch

import com.mongodb.casbah.Imports._
import io.fsq.twofishes.gen._
import io.fsq.twofishes.indexer.importers.geonames._
import io.fsq.twofishes.indexer.mongo.MongoGeocodeStorageService
import salat._
import salat.annotations._
import salat.dao._
import salat.global._

object UpdatePolys {
  def main(args: Array[String]) {
    val store = new MongoGeocodeStorageService()
    // new PolygonLoader.load(store, GeonamesNamespace)
  }
}
