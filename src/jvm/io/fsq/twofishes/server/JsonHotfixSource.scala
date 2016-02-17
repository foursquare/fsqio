// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.
package io.fsq.twofishes.server

import io.fsq.spindle.common.thrift.json.TReadableJSONProtocol
import io.fsq.twofishes.gen.{GeocodeServingFeatureEdit, RawGeocodeServingFeatureEdits}
import java.io.File
import org.apache.thrift.TDeserializer
import org.slf4s.Logging

class JsonHotfixSource(originalPath: String) extends HotfixSource with Logging {
  val deserializer = new TDeserializer(new TReadableJSONProtocol.Factory())
  var path = ""
  var allEdits: Seq[GeocodeServingFeatureEdit] = Seq()

  def init() {
    // reread canonical path in case symlink was modified between refreshes
    path = new File(originalPath).getCanonicalPath
    val dir = new File(path)
    if (dir.exists && dir.isDirectory) {
      allEdits = dir.listFiles.filter(f => f.getName.endsWith(".json")).flatMap(file => {
        val edits = new RawGeocodeServingFeatureEdits
        deserializer.deserialize(edits, scala.io.Source.fromFile(file).getLines().toList.mkString("").getBytes)
        edits.edits
      })
    } else {
      log.warn("invalid hotfix directory: %s".format(path))
    }
  }

  def getEdits(): Seq[GeocodeServingFeatureEdit] = allEdits

  def refresh() {
    init()
  }

  init()
}
