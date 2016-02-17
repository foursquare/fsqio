// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.
package io.fsq.twofishes.indexer.importers.geonames

import io.fsq.twofishes.indexer.util.FsqSimpleFeatureImplicits._
import io.fsq.twofishes.indexer.util.ShapefileIterator
import java.io.FileWriter
import org.slf4s.Logging

// Tool to flatten NaturalEarth Populated Places Shapefile to a single text file
// to simplify scalding index build
// run from twofishes root directory using the following command:
// ./sbt "indexer/run-main io.fsq.twofishes.indexer.importers.geonames.NaturalEarthAttributesFlattener"
//
// NOTE: This is a temporary workaround until I find/write an implementation
// of FileInputFormat and RecordReader for shapefiles that can split
// https://github.com/mraad/Shapefile works but cannot split yet
object NaturalEarthAttributesFlattener extends Logging {

  def main(args: Array[String]): Unit = {
    val fileWriter = new FileWriter("src/jvm/io/fsq/twofishes/indexer/data/downloaded/flattenedAttributes.txt", false)

    var features = 0
    val iterator = new ShapefileIterator("src/jvm/io/fsq/twofishes/indexer/data/downloaded/ne_10m_populated_places_simple.shp")

    for {
      f <- iterator
      geonameidString <- f.propMap.get("geonameid").toList
      // remove .000
      geonameId = geonameidString.toDouble.toInt
      if geonameId != -1
      adm0cap = f.propMap.getOrElse("adm0cap", "0").toDouble.toInt
      worldcity = f.propMap.getOrElse("worldcity", "0").toDouble.toInt
      scalerank = f.propMap.getOrElse("scalerank", "20").toInt
      natscale = f.propMap.getOrElse("natscale", "0").toInt
      labelrank = f.propMap.getOrElse("labelrank", "0").toInt
    } {
      fileWriter.write("%d\t%d\t%d\t%d\t%d\t%d\n".format(
        geonameId,
        adm0cap,
        worldcity,
        scalerank,
        natscale,
        labelrank))

      features += 1
      if (features % 1000 == 0) {
        log.info("processed %d features".format(features))
      }
    }

    fileWriter.close()
    log.info("Done.")
  }
}
