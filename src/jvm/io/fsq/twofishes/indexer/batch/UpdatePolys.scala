//  Copyright 2012 Foursquare Labs Inc. All Rights Reserved
package io.fsq.twofishes.indexer.batch

import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.annotations._
import com.novus.salat.dao._
import com.novus.salat.global._
import io.fsq.twofishes.gen._
import io.fsq.twofishes.indexer.importers.geonames._
import io.fsq.twofishes.indexer.mongo.MongoGeocodeStorageService
import io.fsq.twofishes.util.GeonamesNamespace

object UpdatePolys {
  def main(args: Array[String]) {
    val store = new MongoGeocodeStorageService()
    // new PolygonLoader.load(store, GeonamesNamespace)
  }
}
