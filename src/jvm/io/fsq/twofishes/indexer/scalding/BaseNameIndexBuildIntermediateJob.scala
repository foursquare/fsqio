// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.
package io.fsq.twofishes.indexer.scalding

import com.twitter.scalding._
import com.twitter.scalding.typed.TypedSink
import io.fsq.twofishes.gen._
import io.fsq.twofishes.indexer.util.SpindleSequenceFileSource
import io.fsq.twofishes.util.NameNormalizer
import org.apache.hadoop.io.{LongWritable, Text}

class BaseNameIndexBuildIntermediateJob(
  name: String,
  sources: Seq[String],
  args: Args
) extends TwofishesIntermediateJob(name, args) {

  val features = getJobOutputsAsTypedPipe[LongWritable, GeocodeServingFeature](sources).group

  (for {
    (featureId, servingFeature) <- features
    population = servingFeature.scoringFeatures.populationOption.getOrElse(0)
    boost = servingFeature.scoringFeatures.boostOption.getOrElse(0)
    score = population + boost
    normalizedNames = servingFeature.feature.names.map(name => NameNormalizer.normalize(name.name)).distinct
    normalizedName <- normalizedNames
    if normalizedName.nonEmpty
  } yield {
    (new Text(normalizedName) -> (score * -1, featureId.get))
  }).group
    .withReducers(1)
    .toList
    .mapValues({values: List[(Int, Long)] => {
      val ids = values
        .sorted
        .map({case (s: Int, id: Long) => id})
      IntermediateDataContainer.newBuilder.longList(ids).result
    }})
    .write(TypedSink[(Text, IntermediateDataContainer)](SpindleSequenceFileSource[Text, IntermediateDataContainer](outputPath)))
}
