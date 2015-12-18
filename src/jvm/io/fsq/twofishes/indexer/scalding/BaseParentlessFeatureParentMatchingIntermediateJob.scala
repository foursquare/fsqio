// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.
package io.fsq.twofishes.indexer.scalding

import com.twitter.scalding._
import com.twitter.scalding.typed.TypedSink
import com.vividsolutions.jts.geom.prep.PreparedGeometryFactory
import com.vividsolutions.jts.io.WKBReader
import io.fsq.twofishes.gen._
import io.fsq.twofishes.indexer.util.SpindleSequenceFileSource
import io.fsq.twofishes.util.GeoTools
import io.fsq.twofishes.util.Lists.Implicits._
import org.apache.hadoop.io.LongWritable

class BaseParentlessFeatureParentMatchingIntermediateJob(
  name: String,
  featureSources: Seq[String],
  revgeoIndexSources: Seq[String],
  args: Args
) extends TwofishesIntermediateJob(name, args) {

  val features = getJobOutputsAsTypedPipe[LongWritable, ParentMatchingValues](featureSources).group
  val revgeoIndex = getJobOutputsAsTypedPipe[LongWritable, CellGeometries](revgeoIndexSources).group

  def getParentIds(matchingValue: ParentMatchingValue, candidates: Seq[CellGeometry]): Seq[Long] = {
    val wkbReader = new WKBReader()
    for {
      center <- matchingValue.centerOption.toList
      preparedCenterGeom = PreparedGeometryFactory.prepare(GeoTools.pointToGeometry(center))
      woeType <- matchingValue.woeTypeOption.toList
      candidate <- candidates
      if (candidate.fullOption.has(true) || preparedCenterGeom.within(wkbReader.read(candidate.wkbGeometryByteArray)))
      // TODO: use woeType intelligently
      parentId <- candidate.longIdOption
    } yield {
      parentId
    }
  }

  val joined = revgeoIndex.join(features)
  (for {
    (k, (cellGeometries, matchingValues)) <- joined
    matchingValue <- matchingValues.values
    featureId <- matchingValue.featureIdOption.toList
    candidates = cellGeometries.cells
    parentId <- getParentIds(matchingValue, candidates)
  } yield {
    (new LongWritable(featureId) -> IntermediateDataContainer.newBuilder.longValue(parentId).result)
  }).group
    .toList
    .mapValues({parents: List[IntermediateDataContainer] => {
      IntermediateDataContainer.newBuilder.longList(parents.flatMap(_.longValueOption).distinct).result
    }})
    .write(TypedSink[(LongWritable, IntermediateDataContainer)](SpindleSequenceFileSource[LongWritable, IntermediateDataContainer](outputPath)))
}
