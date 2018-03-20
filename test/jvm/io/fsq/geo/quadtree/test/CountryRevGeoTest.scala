// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.geo.quadtree.test

import io.fsq.geo.quadtree.CountryRevGeo
import io.fsq.specs2.FSSpecificationWithJUnit
import org.specs2.matcher.MatchersImplicits

// TODO: See if there's a way to clean up the extra noise this sends to stderr.
class CountryRevGeoTest extends FSSpecificationWithJUnit with MatchersImplicits {
  "basic test" in {
    CountryRevGeo.getNearestCountryCode(40.74, -74) mustEqual Some("US")
    CountryRevGeo.getNearestCountryCode(-19.937205332238577,-55.8489990234375) mustEqual Some("BR")
  }
}
