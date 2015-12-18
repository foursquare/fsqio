// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.
package io.fsq.twofishes.indexer.scalding

import com.twitter.scalding._
import com.twitter.scalding.typed.TypedSink
import io.fsq.twofishes.gen._
import io.fsq.twofishes.indexer.util.SpindleSequenceFileSource
import io.fsq.twofishes.util.GeometryUtils
import io.fsq.twofishes.util.Lists.Implicits._
import org.apache.hadoop.io.LongWritable

class BaseFeatureCenterS2CellIntermediateJob(
  name: String,
  sources: Seq[String],
  args: Args
) extends TwofishesIntermediateJob(name, args) {

  val features = getJobOutputsAsTypedPipe[LongWritable, GeocodeServingFeature](sources)

  features.flatMap({case (id: LongWritable, f: GeocodeServingFeature) => {
    // we do not allow polygon-name matching to override embedded shapes on features
    // those can only be overridden by explicit id-matched polygons
    // prevent matching by not emitting s2cellId here
    if (f.scoringFeatures.hasPolyOption.has(true)) {
      None
    } else {
      val woeType = f.feature.woeType
      val s2Level = PolygonMatchingHelper.getS2LevelForWoeType(woeType)
      val center = f.feature.geometryOrThrow.center
      val centerS2CellId = GeometryUtils.getS2CellIdForLevel(center.lat, center.lng, s2Level).id
      val matchingValue = PolygonMatchingValue.newBuilder
        .featureId(id.get)
        .names(f.feature.names)
        .result
      val matchingKey = PolygonMatchingKey(centerS2CellId, woeType)
      Some(new PolygonMatchingKeyWritable(matchingKey) -> matchingValue)
    }
  }}).group
    .toList
    .mapValues({matchingValues: List[PolygonMatchingValue] => {
      PolygonMatchingValues(matchingValues)
    }})
    .write(TypedSink[(PolygonMatchingKeyWritable, PolygonMatchingValues)](SpindleSequenceFileSource[PolygonMatchingKeyWritable, PolygonMatchingValues](outputPath)))
}
