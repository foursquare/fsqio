// Copyright 2020 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.twofishes.util.test

import io.fsq.twofishes.gen.{FeatureName, FeatureNameFlags, GeocodeFeature, YahooWoeType}
import io.fsq.twofishes.util.NameUtils
import org.junit.{Assert => A, Test}
import org.mockito.ArgumentMatchers.any

class NameUtilsTest {

  @Test
  def bestNameTest(): Unit = {

    def getName(featureNameOpt: Option[FeatureName]): String = {
      featureNameOpt.flatMap(_.nameOption).getOrElse("")
    }

    val featureNamePreferredLongAbbrev =
      FeatureName.newBuilder
        .name("D.C.")
        .flags(Vector(FeatureNameFlags.ABBREVIATION, FeatureNameFlags.PREFERRED))
        .lang("en")
        .result()
    val featureNameShortAbbrev =
      FeatureName.newBuilder.name("DC").flags(Vector(FeatureNameFlags.ABBREVIATION)).lang("en").result()
    val featureNameNoAbbrev =
      FeatureName.newBuilder.name("District of Columbia").flags(Vector(FeatureNameFlags.PREFERRED)).lang("en").result()

    val geocodeFeatureDC =
      GeocodeFeature.newBuilder
        .cc("US")
        .woeType(YahooWoeType.ADMIN1)
        .names(Vector(featureNamePreferredLongAbbrev, featureNameShortAbbrev, featureNameNoAbbrev))
        .result()

    A.assertEquals(
      "Preferred length should weigh more than default abbreviation",
      getName(NameUtils.bestName(geocodeFeatureDC, Some("en"), preferAbbrev = true, preferredLengthOpt = Some(2))),
      "DC"
    )
    A.assertEquals(
      "When no length is set, the preferred abbreviation should be returned",
      getName(NameUtils.bestName(geocodeFeatureDC, Some("en"), preferAbbrev = true)),
      "D.C."
    )

    A.assertEquals(
      "For names with the same score, choose the shorter one",
      getName(NameUtils.bestName(geocodeFeatureDC, Some("en"), preferAbbrev = false)),
      "D.C."
    )

  }

}
