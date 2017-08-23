// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.annotations

import org.json4s.{DefaultFormats, JField, JObject}
import org.json4s.native.JsonMethods.parse
import scala.collection.mutable.{Map => MutableMap}
import scala.io.Source

/*
Use this to read the json produced when spindle is run with --write_annotations_json.
This provides a faster runtime alternative to reflection for enumerating classes & annotations.
*/
object SpindleAnnotations {
  // note: must agree with INDEX_NAME in spindle_rollup_task.py
  val INDEX_NAME = "/json-annotation.index"

  /* @return resource paths for json annotations (as read from index). */
  def paths(): Iterator[String] =
    Source.fromInputStream(getClass.getResourceAsStream(INDEX_NAME)).getLines

  /* @return a JObject per path in this.paths() by parsing resources. */  
  def jsons(): Iterator[JObject] = for (path <- paths()) yield {
    val body = Source.fromInputStream(getClass.getResourceAsStream("/" + path)).mkString
    parse(body).asInstanceOf[JObject]
  }

  /* @return nested map of {binaryName: {key: value}}. */
  def mergedAnnotations(): Map[String, Map[String, String]] = {
    val map = MutableMap.empty[String, MutableMap[String, String]]
    implicit val formats = DefaultFormats
    for (
      JObject(json) <- jsons();
      (binaryName, JObject(submap)) <- json;
      JField(key, jvalue) <- submap
    ) {
      val classAnnotations = map.getOrElseUpdate(binaryName, MutableMap.empty[String, String])
      // NOTE(awinter): intentional crash here if this isn't a String
      classAnnotations.put(key, jvalue.extract[String])
    }
    (for ((k, v) <- map) yield (k, v.toMap)).toMap
  }
}
