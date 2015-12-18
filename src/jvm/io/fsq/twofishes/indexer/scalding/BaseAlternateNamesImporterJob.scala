// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.
package io.fsq.twofishes.indexer.scalding

import com.twitter.scalding._
import com.twitter.scalding.typed.TypedSink
import io.fsq.twofishes.gen._
import io.fsq.twofishes.indexer.importers.geonames.AlternateNameEntry
import io.fsq.twofishes.indexer.util.SpindleSequenceFileSource
import io.fsq.twofishes.util._
import org.apache.hadoop.io.LongWritable

class BaseAlternateNamesImporterJob(
  name: String,
  inputSpec: TwofishesImporterInputSpec,
  args: Args
) extends TwofishesImporterJob(name, inputSpec, args) {

  lines.flatMap(line => {
    val parts = line.split("\t").toList
    if (parts.size < 3) {
      // logger.error("line %d didn't have 4 parts: %s -- %s".format(index, line, parts.mkString(",")))
      None
    } else {
      val nameid = parts(0)
      val featureId = parts(1)
      val lang = parts.lift(2).getOrElse("")

      if (lang != "post") {
        val name = parts(3)
        val isPrefName = parts.lift(4).exists(_ == "1")
        val isShortName = parts.lift(5).exists(_ == "1")
        val isColloquial = parts.lift(6).exists(_ == "1")
        val isHistoric = parts.lift(7).exists(_ == "1")

        StoredFeatureId.fromHumanReadableString(featureId, Some(GeonamesNamespace)).map(fid => {
          val altName = AlternateNameEntry(
            nameId = nameid,
            name = name,
            lang = lang,
            isPrefName = isPrefName,
            isShortName = isShortName,
            isColloquial = isColloquial,
            isHistoric = isHistoric
          ).toFeatureName

          (new LongWritable(fid.longId), altName)
        })
      } else {
        None
      }
    }
  }).group
    .toList
    .mapValues({names: List[FeatureName] => FeatureNames(names)})
    .write(TypedSink[(LongWritable, FeatureNames)](SpindleSequenceFileSource[LongWritable, FeatureNames](outputPath)))
}
