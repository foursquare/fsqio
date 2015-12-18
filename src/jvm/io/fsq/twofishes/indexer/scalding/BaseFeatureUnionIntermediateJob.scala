// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.
package io.fsq.twofishes.indexer.scalding

import com.twitter.scalding._
import com.twitter.scalding.typed.TypedSink
import io.fsq.twofishes.gen._
import io.fsq.twofishes.indexer.util.SpindleSequenceFileSource
import org.apache.hadoop.io.LongWritable

class BaseFeatureUnionIntermediateJob(
  name: String,
  sources: Seq[String],
  args: Args
) extends TwofishesIntermediateJob(name, args) {

  getJobOutputsAsTypedPipe[LongWritable, GeocodeServingFeature](sources)
    .group
    .head
    .write(TypedSink[(LongWritable, GeocodeServingFeature)](SpindleSequenceFileSource[LongWritable, GeocodeServingFeature](outputPath)))
}
