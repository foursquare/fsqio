// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.adapter

import io.fsq.rogue.{Query, QueryHelpers, RogueException}


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
}
