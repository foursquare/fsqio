// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.actions.concrete

import com.twitter.ostrich.stats.Stats
import io.fsq.exceptionator.actions.{IndexActions, NoticeActions}
import io.fsq.exceptionator.model.{MongoOutgoing, NoticeRecord}
import io.fsq.exceptionator.model.io.{BucketId, Incoming, Outgoing}
import io.fsq.exceptionator.util.Logger
import io.fsq.rogue.lift.LiftRogue._
import net.liftweb.json._
import org.bson.types.ObjectId
import scala.collection.JavaConverters._

class ConcreteNoticeActions extends NoticeActions with IndexActions with Logger {
  def get(ids: List[ObjectId]): List[Outgoing] = {
    NoticeRecord.where(_.id in ids).fetch.map(MongoOutgoing(_))
  }

  def search(keywords: List[String], limit: Option[Int]) = {
    NoticeRecord
      .where(_.keywords all keywords)
      .orderDesc(_.id)
      .limitOpt(limit)
      .hint(NoticeRecord.keywordIndex)
      .fetch
      .map(MongoOutgoing(_))
  }

  def ensureIndexes {
    Vector(NoticeRecord).foreach(metaRecord => {
      metaRecord.mongoIndexList.foreach(i =>
        metaRecord.createIndex(JObject(i.asListMap.map(fld => JField(fld._1, JInt(fld._2.toString.toInt))).toList)))
    })
  }

  def save(incoming: Incoming, tags: Set[String], keywords: Set[String], buckets: Set[BucketId]): NoticeRecord = {
    NoticeRecord.createRecordFrom(incoming)
      .keywords(keywords.toList)
      .tags(tags.toList)
      .buckets(buckets.toList.map(_.toString))
      .save(true)
  }

  def addBucket(id: ObjectId, bucketId: BucketId) {
    Stats.time("incomingActions.addBucket") {
      NoticeRecord.where(_.id eqs id).modify(_.buckets push bucketId.toString).updateOne()
    }
  }

  def removeBucket(id: ObjectId, bucketId: BucketId) {
    val result = Stats.time("noticeActions.removeBucket") {
      NoticeRecord.where(_.id eqs id)
      .select(_.buckets)
      .findAndModify(_.buckets pull bucketId.toString).updateOne(true)
    }

    if (result.exists(_.isEmpty)) {
      logger.debug("deleting " + id.toString)
      Stats.time("noticeActions.removeBucket.deleteRecord") {
        NoticeRecord.delete("_id", id)
      }
    }
  }
}
