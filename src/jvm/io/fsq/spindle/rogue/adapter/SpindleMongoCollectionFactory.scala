// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.rogue.adapter

import com.mongodb.{BasicDBObject, ReadPreference, WriteConcern}
import io.fsq.common.scala.Identity._
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
  MongoCollection,
  Object,
  BasicDBObject,
  UntypedMetaRecord,
  UntypedRecord
] {

  private val indexCache: ConcurrentMap[UntypedMetaRecord, Option[Seq[UntypedMongoIndex]]] = {
    new ConcurrentHashMap[UntypedMetaRecord, Option[Seq[UntypedMongoIndex]]].asScala
  }

  override def documentClass: Class[BasicDBObject] = classOf[BasicDBObject]

  override def documentToString(document: BasicDBObject): String = document.toString

  override def getCodecRegistryFromQuery[M <: UntypedMetaRecord](
    query: Query[M, _, _]
  ): CodecRegistry = {
    clientManager.getCodecRegistryOrThrow(MongoIdentifier(SpindleHelpers.getIdentifier(query.meta)))
  }

  override def getMongoCollectionFromQuery[M <: UntypedMetaRecord](
    query: Query[M, _, _],
    readPreferenceOpt: Option[ReadPreference] = None,
    writeConcernOpt: Option[WriteConcern] = None
  ): MongoCollection[BasicDBObject] = {
    clientManager.useCollection(
      MongoIdentifier(SpindleHelpers.getIdentifier(query.meta)),
      query.collectionName,
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
    clientManager.useCollection(
      MongoIdentifier(SpindleHelpers.getIdentifier(record.meta)),
      SpindleHelpers.getCollection(record.meta),
      documentClass,
      readPreferenceOpt,
      writeConcernOpt
    )(
      identity
    )
  }

  override def getInstanceNameFromQuery[M <: UntypedMetaRecord](
    query: Query[M, _, _]
  ): String = {
    MongoIdentifier(SpindleHelpers.getIdentifier(query.meta)).toString
  }

  override def getInstanceNameFromRecord[R <: UntypedRecord](record: R): String = {
    MongoIdentifier(SpindleHelpers.getIdentifier(record.meta)).toString
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

  /**
   * Retrieves the list of indexes declared for the record type associated with a
   * query. If the record type doesn't declare any indexes, then returns None.
   * @param query the query
   * @return the list of indexes, or an empty list.
   *
   * TODO(jacob): The Option here is a bit superfluous, just return a possibly empty Seq.
   */
  override def getIndexes[
    M <: UntypedMetaRecord
  ](
    query: Query[M, _, _]
  ): Option[Seq[UntypedMongoIndex]] = {
    indexCache.getOrElseUpdate(query.meta, {
      IndexParser.parse(query.meta.annotations).right.toOption.map(indexes => {
        for (index <- indexes) yield {
          val indexEntries = index.map(entry => {
            val wireName = fieldNameToWireName(query.meta, entry.fieldName.split('.')).getOrElse(
              throw new Exception(s"Struct ${query.meta} declares an index on non-existent field ${entry.fieldName}")
            )
            (wireName, entry.indexType)
          })

          DefaultUntypedMongoIndex(indexEntries)
        }
      })
    })
  }
}
