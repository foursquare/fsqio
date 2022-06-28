// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.rogue.adapter

import com.mongodb.{BasicDBObject, ReadPreference, WriteConcern}
import io.fsq.common.scala.Identity._
import io.fsq.common.scala.TryO
import io.fsq.rogue.Query
import io.fsq.rogue.adapter.MongoCollectionFactory
import io.fsq.rogue.connection.{MongoClientManager, MongoIdentifier}
import io.fsq.rogue.index.{DefaultUntypedMongoIndex, UntypedMongoIndex}
import io.fsq.spindle.rogue.SpindleHelpers
import io.fsq.spindle.runtime.{IndexParser, StructFieldDescriptor, UntypedMetaRecord, UntypedRecord}
import java.util.concurrent.ConcurrentHashMap
import org.bson.codecs.configuration.CodecRegistry
import scala.collection.JavaConverters.mapAsScalaConcurrentMapConverter
import scala.collection.concurrent.{Map => ConcurrentMap}

class SpindleMongoCollectionFactory[
  MongoClient,
  MongoDatabase,
  MongoCollection[_]
](
  clientManager: MongoClientManager[MongoClient, MongoDatabase, MongoCollection]
) extends MongoCollectionFactory[
    MongoDatabase,
    MongoCollection,
    Object,
    BasicDBObject,
    UntypedMetaRecord,
    UntypedRecord
  ] {

  private val indexCache: ConcurrentMap[UntypedMetaRecord, Option[Seq[UntypedMongoIndex]]] = {
    new ConcurrentHashMap[UntypedMetaRecord, Option[Seq[UntypedMongoIndex]]].asScala
  }

  protected def getIdentifier(meta: UntypedMetaRecord): MongoIdentifier = {
    MongoIdentifier(SpindleHelpers.getIdentifier(meta))
  }

  override def documentClass: Class[BasicDBObject] = classOf[BasicDBObject]

  override def documentToString(document: BasicDBObject): String = document.toString

  override def getCodecRegistryFromQuery[M <: UntypedMetaRecord](
    query: Query[M, _, _]
  ): CodecRegistry = {
    clientManager.getCodecRegistryOrThrow(getIdentifier(query.meta))
  }

  override def getMongoCollectionFromQuery[M <: UntypedMetaRecord](
    query: Query[M, _, _],
    readPreferenceOpt: Option[ReadPreference] = None,
    writeConcernOpt: Option[WriteConcern] = None
  ): MongoCollection[BasicDBObject] = {
    clientManager.useCollection(
      getIdentifier(query.meta),
      query.collectionName,
      documentClass,
      readPreferenceOpt,
      writeConcernOpt
    )(
      identity
    )
  }

  override def getShardKeyNameFromRecord[R <: UntypedRecord](record: R): Option[String] = {
    val meta = record.meta
    // annotation looks like: `shard_key="id.userId:hashed"
    val shardKeyOpt: Option[String] = meta.annotations.get("shard_key").flatMap(_.split(':').headOption)
    // parts from above example: `Vector(id, userId)`
    val partsOpt: Option[Seq[String]] = shardKeyOpt.map(_.split('.'))
    partsOpt.map(
      parts =>
        fieldNameToWireName(meta, parts).getOrElse(
          throw new Exception(s"Struct $meta declares a shard key on non-existent field $parts")
        )
    )
  }

  override def getMongoCollectionFromMetaRecord[M <: UntypedMetaRecord](
    meta: M,
    readPreferenceOpt: Option[ReadPreference] = None,
    writeConcernOpt: Option[WriteConcern] = None
  ): MongoCollection[BasicDBObject] = {
    clientManager.useCollection(
      getIdentifier(meta),
      SpindleHelpers.getCollection(meta),
      documentClass,
      readPreferenceOpt,
      writeConcernOpt
    )(
      identity
    )
  }

  override def getMongoCollectionFromRecord[R <: UntypedRecord](
    record: R,
    readPreferenceOpt: Option[ReadPreference] = None,
    writeConcernOpt: Option[WriteConcern] = None
  ): MongoCollection[BasicDBObject] = {
    getMongoCollectionFromMetaRecord(
      record.meta,
      readPreferenceOpt = readPreferenceOpt,
      writeConcernOpt = writeConcernOpt
    )
  }

  def getMongoDatabaseFromQuery[M <: UntypedMetaRecord](query: Query[M, _, _]): MongoDatabase = {
    clientManager.use(getIdentifier(query.meta))(identity)
  }

  override def getInstanceNameFromQuery[M <: UntypedMetaRecord](
    query: Query[M, _, _]
  ): String = {
    getIdentifier(query.meta).toString
  }

  override def getInstanceNameFromRecord[R <: UntypedRecord](record: R): String = {
    getIdentifier(record.meta).toString
  }

  private def fieldNameToWireName(
    meta: UntypedMetaRecord,
    parts: Seq[String]
  ): Option[String] = parts match {
    case Seq() => None

    case fieldName +: remainder => {
      val fieldOpt = meta.untypedFields.find(_.longName =? fieldName)
      val wireNameOpt = fieldOpt.map(_.name)

      if (remainder.isEmpty) {
        wireNameOpt
      } else {
        for {
          wireName <- wireNameOpt
          structField <- fieldOpt.collect({ case s: StructFieldDescriptor[_, _, _, _] => s })
          remainingWireName <- fieldNameToWireName(structField.structMeta, remainder)
        } yield s"$wireName.$remainingWireName"
      }
    }
  }

  override def getIndexes(meta: UntypedMetaRecord): Option[Seq[UntypedMongoIndex]] = {
    indexCache.getOrElseUpdate(
      meta, {
        IndexParser
          .parse(meta.annotations)
          .right
          .toOption
          .map(indexes => {
            for (index <- indexes) yield {
              val indexEntries = index.map(entry => {
                val wireName = fieldNameToWireName(meta, entry.fieldName.split('.')).getOrElse(
                  throw new Exception(s"Struct $meta declares an index on non-existent field ${entry.fieldName}")
                )
                (wireName, TryO.toInt(entry.indexType).getOrElse(entry.indexType))
              })

              DefaultUntypedMongoIndex(indexEntries)
            }
          })
      }
    )
  }
}
