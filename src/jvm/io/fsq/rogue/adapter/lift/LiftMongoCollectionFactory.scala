// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.adapter.lift

import com.mongodb.{BasicDBObject, ReadPreference, WriteConcern}
import io.fsq.rogue.Query
import io.fsq.rogue.adapter.MongoCollectionFactory
import io.fsq.rogue.connection.{MongoClientManager, MongoIdentifier}
import io.fsq.rogue.index.{IndexedRecord, UntypedMongoIndex}
import net.liftweb.mongodb.record.{MongoMetaRecord, MongoRecord}
import org.bson.codecs.configuration.CodecRegistry


/** A collection factory for lift models.
  *
  * Example usage:
  *
  *   val blockingClientManager = new BlockingMongoClientManager
  *   val blockingLiftCollectionFactory = new LiftMongoCollectionFactory(blockingClientManager)
  *   val blockingLiftClientAdapter = new BlockingMongoClientAdapter(blockingLiftCollectionFactory)
  *   val blockingLiftQueryExecutor = new QueryExecutor(blockingLiftClientAdapter, new QueryOptimizer)
  */
class LiftMongoCollectionFactory[
  MongoClient,
  MongoDatabase,
  MongoCollection[_]
](
  clientManager: MongoClientManager[MongoClient, MongoDatabase, MongoCollection]
) extends MongoCollectionFactory[
  MongoCollection,
  Object,
  BasicDBObject,
  MongoRecord[_] with MongoMetaRecord[_],
  MongoRecord[_]
] {

  override def documentClass: Class[BasicDBObject] = classOf[BasicDBObject]

  override def documentToString(document: BasicDBObject): String = document.toString

  override def getCodecRegistryFromQuery[M <: MongoRecord[_] with MongoMetaRecord[_]](
    query: Query[M, _, _]
  ): CodecRegistry = {
    clientManager.getCodecRegistryOrThrow(MongoIdentifier(query.meta.connectionIdentifier.jndiName))
  }

  override def getMongoCollectionFromQuery[M <: MongoRecord[_] with MongoMetaRecord[_]](
    query: Query[M, _, _],
    readPreferenceOpt: Option[ReadPreference] = None,
    writeConcernOpt: Option[WriteConcern] = None
  ): MongoCollection[BasicDBObject] = {
    clientManager.useCollection(
      MongoIdentifier(query.meta.connectionIdentifier.jndiName),
      query.collectionName,
      documentClass,
      readPreferenceOpt,
      writeConcernOpt
    )(
      identity
    )
  }

  override def getMongoCollectionFromMetaRecord[M <: MongoRecord[_] with MongoMetaRecord[_]](
    meta: M,
    readPreferenceOpt: Option[ReadPreference] = None,
    writeConcernOpt: Option[WriteConcern] = None
  ): MongoCollection[BasicDBObject] = {
    clientManager.useCollection(
      MongoIdentifier(meta.connectionIdentifier.jndiName),
      meta.collectionName,
      documentClass,
      readPreferenceOpt,
      writeConcernOpt
    )(
      identity
    )
  }

  override def getMongoCollectionFromRecord[R <: MongoRecord[_]](
    record: R,
    readPreferenceOpt: Option[ReadPreference] = None,
    writeConcernOpt: Option[WriteConcern] = None
  ): MongoCollection[BasicDBObject] = {
    clientManager.useCollection(
      MongoIdentifier(record.meta.connectionIdentifier.jndiName),
      record.meta.collectionName,
      documentClass,
      readPreferenceOpt,
      writeConcernOpt
    )(
      identity
    )
  }

  override def getInstanceNameFromQuery[M <: MongoRecord[_] with MongoMetaRecord[_]](
    query: Query[M, _, _]
  ): String = {
    MongoIdentifier(query.meta.connectionIdentifier.jndiName).toString
  }

  override def getInstanceNameFromRecord[R <: MongoRecord[_]](record: R): String = {
    MongoIdentifier(record.meta.connectionIdentifier.jndiName).toString
  }

  /**
   * Retrieves the list of indexes declared for the record type associated with a
   * query. If the record type doesn't declare any indexes, then returns None.
   * @param query the query
   * @return the list of indexes, or an empty list.
   */
  override def getIndexes[
    M <: MongoRecord[_] with MongoMetaRecord[_]
  ](
    query: Query[M, _, _]
  ): Option[Seq[UntypedMongoIndex]] = {
    query.meta match {
      case indexed: IndexedRecord[_] => Some(indexed.mongoIndexList)
      case _ => None
    }
  }
}
