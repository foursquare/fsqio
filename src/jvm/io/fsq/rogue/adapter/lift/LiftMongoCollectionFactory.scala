// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.adapter.lift

import com.mongodb.{BasicDBObject, ReadPreference, WriteConcern}
import io.fsq.rogue.Query
import io.fsq.rogue.adapter.MongoCollectionFactory
import io.fsq.rogue.connection.{MongoClientManager, MongoIdentifier}
import io.fsq.rogue.index.{IndexedRecord, UntypedMongoIndex}
import io.fsq.rogue.shard.ShardedCollection
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
    MongoDatabase,
    MongoCollection,
    Object,
    BasicDBObject,
    MongoRecord[_] with MongoMetaRecord[_],
    MongoRecord[_]
  ] {

  protected def getIdentifier(meta: MongoMetaRecord[_]): MongoIdentifier = {
    MongoIdentifier(meta.connectionIdentifier.jndiName)
  }

  override def documentClass: Class[BasicDBObject] = classOf[BasicDBObject]

  override def documentToString(document: BasicDBObject): String = document.toString

  override def getCodecRegistryFromQuery[M <: MongoRecord[_] with MongoMetaRecord[_]](
    query: Query[M, _, _]
  ): CodecRegistry = {
    clientManager.getCodecRegistryOrThrow(getIdentifier(query.meta))
  }

  override def getMongoCollectionFromQuery[M <: MongoRecord[_] with MongoMetaRecord[_]](
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

  override def getShardKeyNameFromRecord[R <: MongoRecord[_]](record: R): Option[String] = {
    record.meta match {
      case meta: ShardedCollection[_] => Some(meta.shardKey)
      case _ => None
    }
  }

  override def getMongoCollectionFromMetaRecord[M <: MongoRecord[_] with MongoMetaRecord[_]](
    meta: M,
    readPreferenceOpt: Option[ReadPreference] = None,
    writeConcernOpt: Option[WriteConcern] = None
  ): MongoCollection[BasicDBObject] = {
    clientManager.useCollection(
      getIdentifier(meta),
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
      getIdentifier(record.meta),
      record.meta.collectionName,
      documentClass,
      readPreferenceOpt,
      writeConcernOpt
    )(
      identity
    )
  }

  def getMongoDatabaseFromQuery[M <: MongoRecord[_] with MongoMetaRecord[_]](query: Query[M, _, _]): MongoDatabase = {
    clientManager.use(getIdentifier(query.meta))(identity)
  }

  override def getInstanceNameFromQuery[M <: MongoRecord[_] with MongoMetaRecord[_]](
    query: Query[M, _, _]
  ): String = {
    getIdentifier(query.meta).toString
  }

  override def getInstanceNameFromRecord[R <: MongoRecord[_]](record: R): String = {
    getIdentifier(record.meta).toString
  }

  override def getIndexes(
    meta: MongoRecord[_] with MongoMetaRecord[_]
  ): Option[Seq[UntypedMongoIndex]] = {
    meta match {
      case indexed: IndexedRecord[_] => Some(indexed.mongoIndexList)
      case _ => None
    }
  }
}
