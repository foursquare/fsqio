// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.
package io.fsq.exceptionator.model

import _root_.io.fsq.exceptionator.model.io.{BucketId, Outgoing, RichIncoming}
import _root_.io.fsq.spindle.common.thrift.json.TReadableJSONProtocol
import net.liftweb.json
import net.liftweb.json.JsonDSL._
import net.liftweb.json._
import org.apache.thrift.TSerializer
import org.bson.types.ObjectId
import org.joda.time.DateTime

object MongoOutgoing {
  lazy val factory = new TReadableJSONProtocol.Factory(true)
  def apply(nr: RichNoticeRecord): MongoOutgoing = {
    val serializer = new TSerializer(factory)
    val inc: RichIncoming = RichIncoming(nr.noticeOrThrow)
    val incJValue: JValue = json.parse(serializer.toString(nr.noticeOrThrow))

    val merged = {
      (("id" -> nr.id.toString) ~
        ("d" -> nr.id.getTimestamp * 1000L) ~
        ("kw" -> nr.keywords) ~
        ("tags" -> nr.tags) ~
        ("bkts" -> nr.buckets
          .map(id => {
            val bId = BucketId(id)
            bId.name -> Map("nm" -> bId.name, "k" -> bId.key)
          })
          .toMap)) merge incJValue
    }
    MongoOutgoing(nr.id, merged)
  }
}

case class MongoOutgoing(id: ObjectId, doc: JValue) extends Outgoing {
  // Note: this will overwrite any buckets on the existing doc with those given as arguments
  def addBuckets(
    buckets: Seq[RichBucketRecord],
    histograms: Seq[RichBucketRecordHistogram],
    now: DateTime
  ): Outgoing = {
    val bucketInfo: List[JField] = buckets
      .map(bucket => {
        val id = BucketId(bucket.id)
        val histoMaps = List(HistogramType.Hour, HistogramType.Day, HistogramType.Month)
          .map(t => {
            val matched = histograms.filter(h => bucket.id == h.bucket && t == h.histogramType)
            t -> matched.map(_.toEpochMap(now)).flatten.toMap
          })
          .toMap
        JField(
          id.name,
          ("nm" -> id.name) ~
            ("k" -> id.key) ~
            ("df" -> bucket.firstSeen) ~
            ("dl" -> bucket.lastSeen) ~
            ("vf" -> bucket.firstVersionOrThrow) ~
            ("vl" -> bucket.lastVersionOrThrow) ~
            ("h" ->
              ("h" -> histoMaps.get(HistogramType.Hour)) ~
                ("d" -> histoMaps.get(HistogramType.Day)) ~
                ("m" -> histoMaps.get(HistogramType.Month)))
        )
      })
      .toList
    MongoOutgoing(id, JObject(List(JField("bkts", JObject(bucketInfo)))) merge doc)
  }
}
