// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.
package io.fsq.exceptionator.model

import _root_.io.fsq.exceptionator.model.io.{BucketId, Outgoing}
import _root_.io.fsq.rogue.lift.LiftRogue._
import net.liftweb.json.JsonDSL._
import net.liftweb.json._
import org.bson.types.ObjectId
import org.joda.time.DateTime


object MongoOutgoing {
  def apply(nr: NoticeRecord): MongoOutgoing = {
    val ast = nr.asJValue
    val merged = {
      (("id" -> nr.id.toString) ~
      ("d" -> nr.id.value.getTimestamp * 1000L) ~
      ("kw" -> nr.keywords.value) ~
      ("tags" -> nr.tags.value) ~
      ("bkts" -> nr.buckets.value.map(id => {
        val bId = BucketId(id)
        bId.name -> Map("nm" -> bId.name, "k" -> bId.key)
      }).toMap)) merge (ast \ nr.notice.name)
    }
    MongoOutgoing(nr.id.value, merged)
  }
}


case class MongoOutgoing(id: ObjectId, doc: JValue) extends Outgoing {
  // Note: this will overwrite any buckets on the existing doc with those given as arguments
  def addBuckets(buckets: List[BucketRecord], histograms: List[BucketRecordHistogram], now: DateTime): Outgoing = {
    val bucketInfo: List[JField] = buckets.map(bucket => {
      val id = BucketId(bucket.id)
      val histoMaps = List(HistogramType.Hour, HistogramType.Day, HistogramType.Month).map(t => {
        val matched = histograms.filter(h => bucket.id == h.bucket && t == h.histogramType)
        t -> matched.map(_.toEpochMap(now)).flatten.toMap
      }).toMap
      JField(id.name,
        ("nm" -> id.name) ~
        ("k" -> id.key) ~
        ("df" -> bucket.firstSeen.value) ~
        ("dl" -> bucket.lastSeen.value) ~
        ("vf" -> bucket.firstVersion.value) ~
        ("vl" -> bucket.lastVersion.value) ~
        ("h" ->
          ("h" -> histoMaps.get(HistogramType.Hour))~
          ("d" -> histoMaps.get(HistogramType.Day))~
          ("m" -> histoMaps.get(HistogramType.Month))))
    })
    MongoOutgoing(id, JObject(List(JField("bkts", JObject(bucketInfo)))) merge doc)
  }
}
