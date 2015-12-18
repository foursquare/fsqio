// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.
package io.fsq.twofishes.indexer.scalding

import com.twitter.scalding._
import com.twitter.scalding.typed.TypedSink
import io.fsq.twofishes.gen._
import io.fsq.twofishes.indexer.util.SpindleSequenceFileSource
import java.nio.ByteBuffer
import org.apache.hadoop.io.LongWritable

class BasePolygonIndexBuildIntermediateJob(
  name: String,
  sources: Seq[String],
  args: Args
) extends TwofishesIntermediateJob(name, args) {

  val features = getJobOutputsAsTypedPipe[LongWritable, GeocodeServingFeature](sources).group

  (for {
    (featureId, servingFeature) <- features
    if servingFeature.feature.geometryOrThrow.wkbGeometryIsSet
    wkbGeometryByteBuffer = ByteBuffer.wrap(servingFeature.feature.geometryOrThrow.wkbGeometryByteArray)
  } yield {
    (featureId -> IntermediateDataContainer.newBuilder.bytes(wkbGeometryByteBuffer).result)
  }).group
    .withReducers(1)
    .head
    .write(TypedSink[(LongWritable, IntermediateDataContainer)](SpindleSequenceFileSource[LongWritable, IntermediateDataContainer](outputPath)))
}
