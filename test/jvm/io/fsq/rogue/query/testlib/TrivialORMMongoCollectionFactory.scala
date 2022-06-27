// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.query.testlib

import com.mongodb.{ReadPreference, WriteConcern}
import io.fsq.rogue.Query
import io.fsq.rogue.adapter.MongoCollectionFactory
import io.fsq.rogue.connection.MongoClientManager
import io.fsq.rogue.index.{IndexedRecord, UntypedMongoIndex}
import org.bson.Document
import org.bson.codecs.configuration.CodecRegistry

class TrivialORMMongoCollectionFactory[MongoClient, MongoDatabase, MongoCollection[_]](
  clientManager: MongoClientManager[MongoClient, MongoDatabase, MongoCollection]
) extends MongoCollectionFactory[
    MongoDatabase,
    MongoCollection,
    Object,
    Document,
    TrivialORMMetaRecord[_],
    TrivialORMRecord
  ] {

  override def documentClass: Class[Document] = classOf[Document]

  override def documentToString(document: Document): String = document.toJson

  override def getCodecRegistryFromQuery[M <: TrivialORMMetaRecord[_]](
    query: Query[M, _, _]
  ): CodecRegistry = {
    clientManager.getCodecRegistryOrThrow(query.meta.mongoIdentifier)
  }

  override def getShardKeyNameFromRecord[R <: TrivialORMRecord](record: R): Option[String] = None

  override def getMongoCollectionFromQuery[M <: TrivialORMMetaRecord[_]](
    query: Query[M, _, _],
    readPreferenceOpt: Option[ReadPreference] = None,
    writeConcernOpt: Option[WriteConcern] = None
  ): MongoCollection[Document] = {
    clientManager.useCollection(
      query.meta.mongoIdentifier,
      query.collectionName,
      documentClass,
      readPreferenceOpt,
      writeConcernOpt
    )(
      identity
    )
  }

  override def getMongoCollectionFromMetaRecord[M <: TrivialORMMetaRecord[_]](
    meta: M,
    readPreferenceOpt: Option[ReadPreference] = None,
    writeConcernOpt: Option[WriteConcern] = None
  ): MongoCollection[Document] = {
    clientManager.useCollection(
      meta.mongoIdentifier,
      meta.collectionName,
      documentClass,
      readPreferenceOpt,
      writeConcernOpt
    )(
      identity
    )
  }

  override def getMongoCollectionFromRecord[R <: TrivialORMRecord](
    record: R,
    readPreferenceOpt: Option[ReadPreference] = None,
    writeConcernOpt: Option[WriteConcern] = None
  ): MongoCollection[Document] = {
    getMongoCollectionFromMetaRecord(
      record.meta,
      readPreferenceOpt = readPreferenceOpt,
      writeConcernOpt = writeConcernOpt
    )
  }

  def getMongoDatabaseFromQuery[M <: TrivialORMMetaRecord[_]](
    query: Query[M, _, _]
  ): MongoDatabase = {
    clientManager.use(query.meta.mongoIdentifier)(identity)
  }

  override def getInstanceNameFromQuery[M <: TrivialORMMetaRecord[_]](
    query: Query[M, _, _]
  ): String = {
    query.meta.mongoIdentifier.toString
  }

  override def getInstanceNameFromRecord[R <: TrivialORMRecord](record: R): String = {
    record.meta.mongoIdentifier.toString
  }

  override def getIndexes(
    meta: TrivialORMMetaRecord[_]
  ): Option[Seq[UntypedMongoIndex]] = {
    meta match {
      case indexed: IndexedRecord[_] => Some(indexed.mongoIndexList)
      case _ => None
    }
  }
}
