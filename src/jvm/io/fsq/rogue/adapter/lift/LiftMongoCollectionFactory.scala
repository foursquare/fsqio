// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.adapter.lift

import com.mongodb.{BasicDBObject, ReadPreference}
import io.fsq.rogue.Query
import io.fsq.rogue.adapter.MongoCollectionFactory
import io.fsq.rogue.connection.MongoClientManager
import io.fsq.rogue.index.{IndexedRecord, UntypedMongoIndex}
import net.liftweb.mongodb.record.{MongoMetaRecord, MongoRecord}


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
  BasicDBObject,
  MongoRecord[_] with MongoMetaRecord[_],
  MongoRecord[_]
] {

  override def documentClass: Class[BasicDBObject] = classOf[BasicDBObject]

  override def getMongoCollection[
    M <: MongoRecord[_] with MongoMetaRecord[_]
  ](
    query: Query[M, _, _],
    readPreferenceOpt: Option[ReadPreference]
  ): MongoCollection[BasicDBObject] = {
    clientManager.useCollection(
      query.meta.connectionIdentifier,
      query.collectionName,
      documentClass,
      readPreferenceOpt
    )(
      identity
    )
  }

  override def getInstanceName[M <: MongoRecord[_] with MongoMetaRecord[_]](query: Query[M, _, _]): String = {
    query.meta.connectionIdentifier.toString
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
