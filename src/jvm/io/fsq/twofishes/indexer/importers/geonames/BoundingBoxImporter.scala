// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.
package io.fsq.twofishes.indexer.importers.geonames

import io.fsq.twofishes.gen._
import io.fsq.twofishes.indexer.mongo.GeocodeStorageWriteService
import io.fsq.twofishes.indexer.util.{BoundingBox, Point}
import io.fsq.twofishes.util.{GeonamesNamespace, StoredFeatureId}
import java.io.File
import org.slf4s.Logging
import scala.collection.mutable.HashMap

// I could not for the life of me get the java geojson libraries to work
// using a table I computer in python for the flickr bounding boxes.
//
// Please fix at some point.

object BoundingBoxTsvImporter extends Logging {
  def parse(filenames: List[File]): HashMap[StoredFeatureId, BoundingBox] = {
    val map = new HashMap[StoredFeatureId, BoundingBox]
    filenames.foreach(file => {
     log.info("processing bounding box file %s".format(file))
      val lines = scala.io.Source.fromFile(file).getLines
      lines.filterNot(_.startsWith("#")).foreach(line => {
        val parts = line.split("[\t ]")
        // 0: geonameid
        // 1->5:       // west, south, east, north
        if (parts.size != 5) {
          log.error("wrong # of parts: %d vs %d in %s".format(parts.size, 5, line))
        } else {
          try {
            val id = parts(0)
            val w = parts(1).toDouble
            val s = parts(2).toDouble
            val e = parts(3).toDouble
            val n = parts(4).toDouble
            StoredFeatureId.fromHumanReadableString(id, Some(GeonamesNamespace)) match {
              case Some(fid) => {
                map(fid) = BoundingBox(Point(n, e), Point(s, w))
                log.debug("bbox %s %s".format(fid, parts.drop(1).mkString(",")))
              }
              case None => log.error("%s: couldn't parse into StoredFeatureId".format(line))
            }
          } catch {
            case e: Throwable =>
            log.error("%s: %s".format(line, e))
          }
        }
      })
    })
    map
  }

  def batchUpdate(filenames: List[File], store: GeocodeStorageWriteService) = {
    parse(filenames).foreach({case (fid, bbox) => {
      store.addBoundingBoxToRecord(bbox, fid)
    }})
  }
}
