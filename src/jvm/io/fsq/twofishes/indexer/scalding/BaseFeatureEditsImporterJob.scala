// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.
package io.fsq.twofishes.indexer.scalding

import com.twitter.scalding._
import com.twitter.scalding.typed.TypedSink
import io.fsq.twofishes.gen._
import io.fsq.twofishes.indexer.util.SpindleSequenceFileSource
import org.apache.hadoop.io.LongWritable

class BaseFeatureEditsImporterJob(
  name: String,
  lineProcessor: (String) => Option[(LongWritable, GeocodeServingFeatureEdit)],
  inputSpec: TwofishesImporterInputSpec,
  args: Args
) extends TwofishesImporterJob(name, inputSpec, args) {

  lines.filterNot(_.startsWith("#")).flatMap(line => lineProcessor(line))
    .group
    .toList
    .mapValues({edits: List[GeocodeServingFeatureEdit] => GeocodeServingFeatureEdits(edits)})
    .write(TypedSink[(LongWritable, GeocodeServingFeatureEdits)](SpindleSequenceFileSource[LongWritable, GeocodeServingFeatureEdits](outputPath)))
}
