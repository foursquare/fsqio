// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.lift

import com.mongodb.{BasicDBObject, WriteConcern}
import com.mongodb.client.{MongoCollection, MongoDatabase}
import io.fsq.field.Field
import io.fsq.rogue.{
  AddLimit,
  FindAndModifyQuery,
  Iter,
  Limited,
  ModifyQuery,
  Query,
  RequireShardKey,
  Required,
  ShardingOk,
  Skipped,
  Unlimited,
  Unselected,
  Unskipped
}
import io.fsq.rogue.MongoHelpers.MongoSelect
import io.fsq.rogue.Rogue._
import io.fsq.rogue.adapter.BlockingResult
import io.fsq.rogue.query.QueryExecutor

// TODO(jacob): Either delete this file in favor of using a QueryExecutor explicitly, or
//    move it out of the lift package.

case class ExecutableQuery[MB, M <: MB, RB, R, State](
  query: Query[M, R, State],
  executor: QueryExecutor[MongoDatabase, MongoCollection, Object, BasicDBObject, MB, RB, BlockingResult]
)(
  implicit ev: ShardingOk[M, State]
) extends BlockingResult.Implicits {

  /**
    * Gets the size of the query result. This should only be called on queries that do not
    * have limits or skips.
    */
  def count(): Long = {
    executor.count(query)
  }

  /**
    * Returns the number of distinct values returned by a query. The query must not have
    * limit or skip clauses.
    */
  def countDistinct[V](field: M => Field[V, _]): Long = {
    executor.countDistinct(query)(field.asInstanceOf[M => Field[V, M]])
  }

  /**
    * Returns a list of distinct values returned by a query. The query must not have
    * limit or skip clauses.
    */
  def distinct[V](field: M => Field[V, _]): Seq[V] = {
    executor.distinct(query)(field.asInstanceOf[M => Field[V, M]])
  }

  /**
    * Checks if there are any records that match this query.
    */
  def exists()(implicit ev: State <:< Unlimited with Unskipped): Boolean = {
    val q = query.copy(select = Some(MongoSelect[M, Null](Nil, _ => null)))
    executor.fetch(q.limit(1)).nonEmpty
  }

  /**
    * Executes a function on each record value returned by a query.
    * @param f a function to be invoked on each fetched record.
    * @return nothing.
    */
  def foreach(f: R => Unit): Unit = {
    executor.foreach(query)(f)
  }

  /**
    * Execute the query, returning all of the records that match the query.
    * @return a list containing the records that match the query
    */
  def fetch(): List[R] = {
    executor.fetch[M, R, State, List](query)
  }

  /**
    * Execute a query, returning no more than a specified number of result records. The
    * query must not have a limit clause.
    * @param limit the maximum number of records to return.
    */
  def fetch[S2](limit: Int)(implicit ev1: AddLimit[State, S2], ev2: ShardingOk[M, S2]): List[R] = {
    executor.fetch[M, R, S2, List](query.limit(limit))
  }

  /**
    * fetch a batch of results, and execute a function on each element of the list.
    * @param f the function to invoke on the records that match the query.
    * @return a list containing the results of invoking the function on each record.
    */
  def fetchBatch[T](batchSize: Int)(f: Seq[R] => Seq[T]): Seq[T] = {
    executor.fetchBatch[M, R, State, Vector, T](query, batchSize)(f)
  }

  /**
    * Fetches the first record that matches the query. The query must not contain a "limited" clause.
    * @return an option record containing either the first result that matches the
    *         query, or None if there are no records that match.
    */
  def get[S2]()(implicit ev1: AddLimit[State, S2], ev2: ShardingOk[M, S2]): Option[R] = {
    executor.fetchOne(query)
  }

  /**
    * Fetches the records that match the query in paginated form. The query must not contain
    * a "limit" clause.
    * @param countPerPage the number of records to be contained in each page of the result.
    */
  def paginate(
    countPerPage: Int
  )(
    implicit ev1: Required[State, Unlimited with Unskipped],
    ev2: ShardingOk[M, State]
  ): PaginatedQuery[MB, M, RB, R, Unlimited with Unskipped] = {
    new PaginatedQuery(ev1(query), executor, countPerPage)
  }

  /**
    * Delete all of the records that match the query. The query must not contain any "skip",
    * "limit", or "select" clauses. Sends the delete operation to mongo, and returns - does
    * <em>not</em> wait for the delete to be finished.
    */
  def bulkDelete_!!!(
    )(
    implicit ev1: Required[State, Unselected with Unlimited with Unskipped]
  ): Unit = {
    executor.bulkDelete_!!(query)
  }

  /**
    * Delete all of the records that match the query. The query must not contain any "skip",
    * "limit", or "select" clauses. Sends the delete operation to mongo, and waits for the
    * delete operation to complete before returning to the caller.
    */
  def bulkDelete_!!(
    concern: WriteConcern
  )(
    implicit ev1: Required[State, Unselected with Unlimited with Unskipped]
  ): Unit = {
    executor.bulkDelete_!!(query, concern)
  }

  /**
    * Finds the first record that matches the query (if any), fetches it, and then deletes it.
    * A copy of the deleted record is returned to the caller.
    */
  def findAndDeleteOne()(implicit ev: RequireShardKey[M, State]): Option[R] = {
    executor.findAndDeleteOne(query)
  }

  def iterate[S](state: S, batchSizeOpt: Option[Int] = None)(handler: (S, Iter.Event[R]) => Iter.Command[S]): S = {
    executor.iterate(query, state, batchSizeOpt = batchSizeOpt)(handler)
  }

}

case class ExecutableModifyQuery[MB, M <: MB, RB, State](
  query: ModifyQuery[M, State],
  executor: QueryExecutor[MongoDatabase, MongoCollection, Object, BasicDBObject, MB, RB, BlockingResult]
) extends BlockingResult.Implicits {

  def updateMulti(): Unit = {
    executor.updateMany(query)
  }

  def updateOne()(implicit ev: RequireShardKey[M, State]): Unit = {
    executor.updateOne(query)
  }

  def upsertOne()(implicit ev: RequireShardKey[M, State]): Unit = {
    executor.upsertOne(query)
  }

  def updateMulti(writeConcern: WriteConcern): Unit = {
    executor.updateMany(query, writeConcern)
  }

  def updateOne(writeConcern: WriteConcern)(implicit ev: RequireShardKey[M, State]): Unit = {
    executor.updateOne(query, writeConcern)
  }

  def upsertOne(writeConcern: WriteConcern)(implicit ev: RequireShardKey[M, State]): Unit = {
    executor.upsertOne(query, writeConcern)
  }
}

case class ExecutableFindAndModifyQuery[MB, M <: MB, RB, R](
  query: FindAndModifyQuery[M, R],
  executor: QueryExecutor[MongoDatabase, MongoCollection, Object, BasicDBObject, MB, RB, BlockingResult]
) extends BlockingResult.Implicits {

  def updateOne(returnNew: Boolean = false): Option[R] = {
    executor.findAndUpdateOne(query, returnNew)
  }

  def upsertOne(returnNew: Boolean = false): Option[R] = {
    executor.findAndUpsertOne(query, returnNew)
  }
}

class PaginatedQuery[MB, M <: MB, RB, R, +State <: Unlimited with Unskipped](
  q: Query[M, R, State],
  executor: QueryExecutor[MongoDatabase, MongoCollection, Object, BasicDBObject, MB, RB, BlockingResult],
  val countPerPage: Int,
  val pageNum: Int = 1
)(
  implicit ev: ShardingOk[M, State]
) extends BlockingResult.Implicits {

  def copy() = new PaginatedQuery(q, executor, countPerPage, pageNum)

  def setPage(p: Int) = if (p == pageNum) this else new PaginatedQuery(q, executor, countPerPage, p)

  def setCountPerPage(c: Int) = if (c == countPerPage) this else new PaginatedQuery(q, executor, c, pageNum)

  lazy val countAll: Long = {
    executor.count(q)
  }

  def fetch(): List[R] = {
    executor.fetch[M, R, Limited with Skipped, List](
      q.skip(countPerPage * (pageNum - 1)).limit(countPerPage)
    )
  }

  def numPages = math.ceil(countAll.toDouble / countPerPage.toDouble).toInt max 1
}
