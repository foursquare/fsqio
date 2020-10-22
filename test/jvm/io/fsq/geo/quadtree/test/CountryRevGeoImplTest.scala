// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.geo.quadtree.test

import com.vividsolutions.jts.io.WKTReader
import io.fsq.geo.quadtree.{CountryRevGeo, CountryRevGeoImpl}
import io.fsq.geo.quadtree.ShapefileGeo.{GeoBounds, ShapeTrieNode}
import io.fsq.specs2.FSSpecificationWithJUnit
import org.specs2.matcher.MatchersImplicits

class CountryRevGeoImplTest extends FSSpecificationWithJUnit with MatchersImplicits {
  "basic test" in {
    skipped("This test does not run in CI due to missing shapefile resources")
    CountryRevGeoImpl.getNearestCountryCode(40.74, -74) mustEqual Some("US")
    CountryRevGeoImpl.getNearestCountryCode(-19.937205332238577, -55.8489990234375) mustEqual Some("BR")
  }

  object MockCountryRevGeoImpl extends CountryRevGeo {
    // Bypass shapefile dependence by programmatically indexing geometries for test cases
    val nycGh3 = "POLYGON((-74.53125 39.375,-73.125 39.375,-73.125 40.78125,-74.53125 40.78125,-74.53125 39.375))"
    val brGh3 = "POLYGON((-56.25 -21.09375,-54.84375 -21.09375,-54.84375 -19.6875,-56.25 -19.6875,-56.25 -21.09375))"

    // bounds copied from shapefile example
    val worldBounds = GeoBounds(-179.99990000000003, -89.9999, 359.9998000000001, 173.6273185180664)
    val world = new ShapeTrieNode(0, worldBounds, false)

    world.addFeature(Nil, "US", new WKTReader().read(nycGh3), Array())
    world.addFeature(Nil, "BR", new WKTReader().read(brGh3), Array())

    def getNearestCountryCode(geolat: Double, geolong: Double): Option[String] = {
      world.getNearest(geolat, geolong)
    }
  }

  "mocked TrieNode lookup" in {
    MockCountryRevGeoImpl.getNearestCountryCode(40.74, -74) mustEqual Some("US")
    MockCountryRevGeoImpl.getNearestCountryCode(-19.937205332238577, -55.8489990234375) mustEqual Some("BR")
  }
}
