// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.
package io.fsq.twofishes.core

import io.fsq.twofishes.gen.YahooWoeType
import io.fsq.twofishes.gen.YahooWoeType.{ADMIN1, ADMIN2, ADMIN3, AIRPORT, COUNTRY, POI, POSTAL_CODE, SUBURB, TOWN}

object YahooWoeTypes {
  val order = List(POSTAL_CODE, AIRPORT, POI, SUBURB, TOWN, ADMIN3, ADMIN2, ADMIN1, COUNTRY)
  val orderMap = order.zipWithIndex.toList.toMap

  val postalCodeWoeTypes: Set[YahooWoeType] = Set(YahooWoeType.POSTAL_CODE)
  val neighborhoodWoeTypes: Set[YahooWoeType] = Set(YahooWoeType.SUBURB, YahooWoeType.TOWN)
  val cityWoeTypes: Set[YahooWoeType] = Set(YahooWoeType.SUBURB, YahooWoeType.TOWN, YahooWoeType.ADMIN3)
  val countyWoeTypes: Set[YahooWoeType] = Set(YahooWoeType.ADMIN2)
  val provinceWoeTypes: Set[YahooWoeType] = Set(YahooWoeType.ADMIN2, YahooWoeType.ADMIN1)
  val countryWoeTypes: Set[YahooWoeType] = Set(YahooWoeType.COUNTRY)

  def getOrdering(woetype: YahooWoeType): Int = {
    orderMap.get(woetype).getOrElse(-1)
  }

  def compare(woeTypeA: YahooWoeType, woeTypeB: YahooWoeType): Int = {
    val A = orderMap.get(woeTypeA).getOrElse(Int.MaxValue)
    val B = orderMap.get(woeTypeB).getOrElse(Int.MaxValue)
    (A.compare(B))
  }

  val isAdminWoeTypes = Set(ADMIN3, ADMIN2, ADMIN1, COUNTRY)
  def isAdminWoeType(woeType: YahooWoeType): Boolean =
    isAdminWoeTypes.contains(woeType)
}
