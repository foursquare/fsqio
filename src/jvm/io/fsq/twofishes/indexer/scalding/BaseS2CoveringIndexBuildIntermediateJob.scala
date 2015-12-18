// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.
package io.fsq.twofishes.indexer.scalding

import com.twitter.scalding._
import com.twitter.scalding.typed.TypedSink
import com.vividsolutions.jts.io.WKBReader
import io.fsq.twofishes.gen._
import io.fsq.twofishes.indexer.util.SpindleSequenceFileSource
import io.fsq.twofishes.util.{GeometryUtils, S2CoveringConstants}
import org.apache.hadoop.io.LongWritable

class BaseS2CoveringIndexBuildIntermediateJob(
  name: String,
  sources: Seq[String],
  args: Args
) extends TwofishesIntermediateJob(name, args) {

  val features = getJobOutputsAsTypedPipe[LongWritable, GeocodeServingFeature](sources).group

  (for {
    (featureId, servingFeature) <- features
    if servingFeature.feature.geometryOrThrow.wkbGeometryIsSet
    geometry = new WKBReader().read(servingFeature.feature.geometryOrThrow.wkbGeometryByteArray)
    cellIds = GeometryUtils.s2PolygonCovering(
      geometry,
      S2CoveringConstants.minS2LevelForS2Covering,
      S2CoveringConstants.maxS2LevelForS2Covering,
      levelMod = Some(S2CoveringConstants.defaultLevelModForS2Covering),
      maxCellsHintWhichMightBeIgnored = Some(S2CoveringConstants.defaultMaxCellsHintForS2Covering)
    ).map(_.id)
  } yield {
    (featureId -> IntermediateDataContainer.newBuilder.longList(cellIds).result)
  }).group
    .withReducers(1)
    .head
    .write(TypedSink[(LongWritable, IntermediateDataContainer)](SpindleSequenceFileSource[LongWritable, IntermediateDataContainer](outputPath)))
}
