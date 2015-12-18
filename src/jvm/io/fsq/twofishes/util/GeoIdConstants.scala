// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.twofishes.util



object GeoIdConstants {

  def makeGeonameIds(ids: Int*): Seq[GeonamesId] = {
    ids.map(i => GeonamesId(i.toLong))
  }

  val WashingtonDcIds = makeGeonameIds(4140963, 4138106) // city, state
  val QueensNyIds = makeGeonameIds(5133273, 5133268)
  val BrooklynNyIds = makeGeonameIds(5110302, 6941775) // city, county
  val ManhattanNyIds = makeGeonameIds(5128581, 5128594) // city, county

  // in greece, these are the admin2s of the attica admin1
  val atticaDepartments = makeGeonameIds(445408, 445406, 445407, 406101)

  // See comment in getStateLike
  val GreekPreferredAdmin3sAsState = makeGeonameIds(8133757, 8133756, 8133747, 8133889, 8133766, 8133849, 263021)
  val GreekBadAdmin2sPreferAdmin3 = makeGeonameIds(263021)

  val SanFranciscoLongId = 72057594043319895L

  // Because New York City is split up into multiple boroughs that are also
  // listed as cities
  val NewYorkLongId = 72057594043056517L
  val ManhattanLongId = 72057594043053707L
  val NewYorkBoroughLongIds = Vector(
    NewYorkLongId,
    ManhattanLongId,    // Manhattan
    72057594043038238L, // Brooklyn
    72057594043067504L, // Staten Island
    72057594043061209L, // Queens
    72057594043038202L  // Bronx
  )

  val NewYorkBoroughCountyLongIds = Vector(
    72057594043056530L, // New York County (Manhattan)
    72057594044869711L, // Brooklyn County
    72057594043067495L, // Staten Island County
    72057594043061204L, // Queens County
    72057594043038189L  // Bronx County
  )

  val NewYorkBoroughGeonames = Vector(
    5128581, // New York City
    5125771, // Manhattan
    5110302, // Brooklyn
    6941775, // Brooklyn County
    5139568, // Staten Island
    5139559, // Staten Island County
    5133273, // Queens
    5110266  // Bronx
  )

  val FiveBoroughId = 9151314442832893801L

  val USALongId = 72057594044179937L

  // feature ids have a provider-specific id in the lower 56 bits
  // and a namespace id in the upper 8 bits. Given that we don't expect to end
  // up with that many namespaces anytime soon, put Byte.MaxValue up there to ensure we don't
  // collide with geonames or maponics at 1 and 0 respectively
  val FakeFeatureNamespaceId = Byte.MaxValue
}
