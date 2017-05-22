// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.query

import com.mongodb.{ReadPreference, WriteConcern}
import io.fsq.field.Field
import io.fsq.rogue.{AddLimit, Query, QueryHelpers, QueryOptimizer, Rogue, ShardingOk, !<:<}
import io.fsq.rogue.adapter.MongoClientAdapter
import io.fsq.rogue.types.MongoDisallowed
import scala.collection.generic.CanBuildFrom
import scala.collection.mutable.ArrayBuffer


/** TODO(jacob): All of the collection methods implemented here should get rid of the
  *     option to send down a read preference, and just use the one on the query.
  */
class QueryExecutor[
  MongoCollection[_],
  DocumentValue,
  Document <: java.util.Map[String, DocumentValue],
  MetaRecord,
  Record,
  Result[_]
](
  adapter: MongoClientAdapter[MongoCollection, DocumentValue, Document, MetaRecord, Record, Result],
  optimizer: QueryOptimizer,
  serializer: RogueSerializer[MetaRecord, Record, Document]
) extends Rogue {

  def defaultWriteConcern: WriteConcern = QueryHelpers.config.defaultWriteConcern

  def count[M <: MetaRecord, State](
    query: Query[M, _, State],
    readPreferenceOpt: Option[ReadPreference] = None
  )(
    implicit ev: ShardingOk[M, State],
    ev2: M !<:< MongoDisallowed
  ): Result[Long] = {
    if (optimizer.isEmptyQuery(query)) {
      adapter.wrapEmptyResult(0L)
    } else {
      adapter.count(query, readPreferenceOpt)
    }
  }

  def countDistinct[M <: MetaRecord, FieldType, State](
    query: Query[M, _, State],
    readPreferenceOpt: Option[ReadPreference] = None
  )(
    field: M => Field[FieldType, M]
  )(
    implicit ev: ShardingOk[M, State],
    ev2: M !<:< MongoDisallowed
  ): Result[Long] = {
    if (optimizer.isEmptyQuery(query)) {
      adapter.wrapEmptyResult(0L)
    } else {
      adapter.countDistinct(query, field(query.meta).name, readPreferenceOpt)
    }
  }

  def distinct[M <: MetaRecord, FieldType, State](
    query: Query[M, _, State],
    readPreferenceOpt: Option[ReadPreference] = None
  )(
    field: M => Field[FieldType, M],
    resultTransformer: DocumentValue => FieldType = (_: DocumentValue).asInstanceOf[FieldType]
  )(
    implicit ev: ShardingOk[M, State], ev2: M !<:< MongoDisallowed
  ): Result[Seq[FieldType]] = {
    if (optimizer.isEmptyQuery(query)) {
      adapter.wrapEmptyResult(Vector.empty[FieldType])
    } else {
      adapter.distinct(query, field(query.meta).name, resultTransformer, readPreferenceOpt)
    }
  }

  def insert[R <: Record](
    record: R,
    writeConcern: WriteConcern = defaultWriteConcern
  ): Result[R] = {
    adapter.insert(record, serializer.writeToDocument(record), Some(writeConcern))
  }

  def fetch[M <: MetaRecord, R <: Record, State, Collection[_]](
    query: Query[M, R, State],
    readPreferenceOpt: Option[ReadPreference] = None
  )(
    implicit ev: ShardingOk[M, State],
    ev2: M !<:< MongoDisallowed,
    canBuildFrom: CanBuildFrom[_, R, Collection[R]]
  ): Result[Collection[R]] = {
    val resultsBuilder = canBuildFrom()

    if (optimizer.isEmptyQuery(query)) {
      adapter.wrapEmptyResult(resultsBuilder.result())
    } else {
      def processor(document: Document): Unit = {
        resultsBuilder += serializer.readFromDocument(query.meta, query.select)(document)
      }
      adapter.query(resultsBuilder.result(), processor)(query, None, readPreferenceOpt)
    }
  }

  def fetchOne[M <: MetaRecord, R <: Record, State, LimitState](
    query: Query[M, R, State],
    readPreferenceOpt: Option[ReadPreference] = None
  )(
    implicit ev: AddLimit[State, LimitState],
    ev2: ShardingOk[M, LimitState],
    ev3: M !<:< MongoDisallowed
  ): Result[Option[R]] = {
    if (optimizer.isEmptyQuery(query)) {
      adapter.wrapEmptyResult(None)
    } else {
      var result: Option[R] = None
      def processor(document: Document): Unit = {
        result = Some(serializer.readFromDocument(query.meta, query.select)(document))
      }
      adapter.query(result, processor)(query.limit(1), None, readPreferenceOpt)
    }
  }

  def foreach[M <: MetaRecord, R <: Record, State](
    query: Query[M, R, State],
    readPreferenceOpt: Option[ReadPreference] = None
  )(
    f: R => Unit
  )(
    implicit ev: ShardingOk[M, State],
    ev2: M !<:< MongoDisallowed
  ): Result[Unit] = {
    if (optimizer.isEmptyQuery(query)) {
      adapter.wrapEmptyResult(())
    } else {
      def processor(document: Document): Unit = {
        f(serializer.readFromDocument(query.meta, query.select)(document))
      }
      adapter.query((), processor)(query, None, readPreferenceOpt)
    }
  }

  def fetchBatch[M <: MetaRecord, R <: Record, State, Collection[_], T](
    query: Query[M, R, State],
    batchSize: Int,
    readPreferenceOpt: Option[ReadPreference] = None
  )(
    f: Seq[R] => Seq[T]
  )(
    implicit ev: ShardingOk[M, State],
    ev2: M !<:< MongoDisallowed,
    canBuildFrom: CanBuildFrom[_, T, Collection[T]]
  ): Result[Collection[T]] = {
    val resultsBuilder = canBuildFrom()

    if (optimizer.isEmptyQuery(query)) {
      adapter.wrapEmptyResult(resultsBuilder.result())
    } else {
      val batchBuffer = new ArrayBuffer[R]

      def processor(document: Document): Unit = {
        batchBuffer += serializer.readFromDocument(query.meta, query.select)(document)
        if (batchBuffer.length >= batchSize) {
          resultsBuilder ++= f(batchBuffer)
          batchBuffer.clear()
        }
      }

      // Process potentially incomplete last batch before collecting the final result.
      def resultAccessor: Collection[T] = {
        resultsBuilder ++= f(batchBuffer)
        resultsBuilder.result()
      }

      adapter.query(resultAccessor, processor)(query, Some(batchSize), readPreferenceOpt)
    }
  }
}
