package io.fsq.twofishes.country.test

import io.fsq.twofishes.country.{CountryInfo, CountryUtils}
import org.specs2.matcher.MatchersImplicits
import org.specs2.mutable.SpecificationWithJUnit

// TODO: See if there's a way to clean up the extra noise this sends to stderr.
class CountryUtilsSpec extends SpecificationWithJUnit with MatchersImplicits {
  "US should exist" in {
    CountryUtils.getNameByCode("US") mustEqual Some("United States")
    CountryUtils.getNameByCode("USA") mustEqual Some("United States")
    CountryUtils.getNameByCode("ABC") mustEqual None
  }

  "timezones should work" in {
    CountryInfo.getCountryInfo("US").get.tzIDs must containTheSameElementsAs(
      List("America/Adak", "America/Anchorage", "America/Boise", "America/Chicago", "America/Denver", "America/Detroit", "America/Indiana/Indianapolis", "America/Indiana/Knox", "America/Indiana/Marengo", "America/Indiana/Petersburg", "America/Indiana/Tell_City", "America/Indiana/Vevay", "America/Indiana/Vincennes", "America/Indiana/Winamac", "America/Juneau", "America/Kentucky/Louisville", "America/Kentucky/Monticello", "America/Los_Angeles", "America/Menominee", "America/Metlakatla", "America/New_York", "America/Nome", "America/North_Dakota/Beulah", "America/North_Dakota/Center", "America/North_Dakota/New_Salem", "America/Phoenix", "America/Sitka", "America/Yakutat", "Pacific/Honolulu")
    )

    CountryInfo.getCountryInfo("DE").get.tzIDs must containTheSameElementsAs(
      List("Europe/Berlin", "Europe/Busingen")
    )
  }
}
