// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.adapter

import com.mongodb.ReadPreference
import com.mongodb.client.model.CountOptions
import io.fsq.rogue.{Query, QueryHelpers, RogueException}
import io.fsq.rogue.MongoHelpers.MongoBuilder
import org.bson.conversions.Bson


abstract class MongoClientAdapter[MongoCollection[_], Document, MetaRecord, Record, Result[_]](
  collectionFactory: MongoCollectionFactory[MongoCollection, Document, MetaRecord, Record]
) {

  /** Wrap an empty result for a no-op query. */
  def wrapEmptyResult[T](value: T): Result[T]

  private def runCommand[M <: MetaRecord, T](
    descriptionFunc: () => String,
    query: Query[M, _, _]
  )(
    f: => T
  ): T = {
    // Use nanoTime instead of currentTimeMillis to time the query since
    // currentTimeMillis only has 10ms granularity on many systems.
    val start = System.nanoTime
    val instanceName: String = collectionFactory.getInstanceName(query)
    // Note that it's expensive to call descriptionFunc, it does toString on the Query
    // the logger methods are call by name
    try {
      QueryHelpers.logger.onExecuteQuery(query, instanceName, descriptionFunc(), f)
    } catch {
      case e: Exception => {
        val timeMs = (System.nanoTime - start) / 1000000
        throw new RogueException(
          s"Mongo query on $instanceName [${descriptionFunc()}] failed after $timeMs ms",
          e
        )
      }
    } finally {
      QueryHelpers.logger.log(query, instanceName, descriptionFunc(), (System.nanoTime - start) / 1000000)
    }
  }

  protected def countImpl(
    collection: MongoCollection[Document]
  )(
    filter: Bson,
    options: CountOptions
  ): Result[Long]

  // TODO(jacob): We should get rid of the option to send down a read preference here and
  //    just use the one on the query.
  def count[
    M <: MetaRecord
  ](
    query: Query[M, _, _],
    readPreferenceOpt: Option[ReadPreference]
  ): Result[Long] = {
    val queryClause = QueryHelpers.transformer.transformQuery(query)
    QueryHelpers.validator.validateQuery(queryClause, collectionFactory.getIndexes(queryClause))
    val collection = collectionFactory.getMongoCollection(query, readPreferenceOpt)
    val descriptionFunc = () => MongoBuilder.buildConditionString("count", query.collectionName, queryClause)
    // TODO(jacob): This cast will always succeed, but it should be removed once there is a
    //    version of MongoBuilder that speaks the new CRUD api.
    val condition = MongoBuilder.buildCondition(queryClause.condition).asInstanceOf[Bson]
    val options = {
      new CountOptions()
        .limit(queryClause.lim.getOrElse(0))
        .skip(queryClause.sk.getOrElse(0))
    }

    runCommand(descriptionFunc, queryClause) {
      countImpl(collection)(condition, options)
    }
  }
}
