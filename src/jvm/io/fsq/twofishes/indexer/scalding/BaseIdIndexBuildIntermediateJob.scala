// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.
package io.fsq.twofishes.indexer.scalding

import com.twitter.scalding._
import com.twitter.scalding.typed.TypedSink
import io.fsq.twofishes.gen._
import io.fsq.twofishes.indexer.util.SpindleSequenceFileSource
import io.fsq.twofishes.util.StoredFeatureId
import org.apache.hadoop.io.{LongWritable, Text}

class BaseIdIndexBuildIntermediateJob(
  name: String,
  sources: Seq[String],
  args: Args
) extends TwofishesIntermediateJob(name, args) {

  val features = getJobOutputsAsTypedPipe[LongWritable, GeocodeServingFeature](sources).group

  (for {
    (featureId, servingFeature) <- features
    slugs = servingFeature.slugs
    // skip primary id
    humanReadableIds = servingFeature.feature.ids.drop(1).map(id => id.source + ":" + id.id)
    longIds = humanReadableIds.flatMap(id => StoredFeatureId.fromHumanReadableString(id)).map(_.longId.toString)
    key <- slugs ++ humanReadableIds ++ longIds
  } yield {
    (new Text(key) -> IntermediateDataContainer.newBuilder.longValue(featureId.get).result)
  }).group
    .withReducers(1)
    .head
    .write(TypedSink[(Text, IntermediateDataContainer)](SpindleSequenceFileSource[Text, IntermediateDataContainer](outputPath)))
}
