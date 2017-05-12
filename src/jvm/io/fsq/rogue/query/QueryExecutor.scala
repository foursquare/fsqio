// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.query

import com.mongodb.{ReadPreference, WriteConcern}
import io.fsq.field.Field
import io.fsq.rogue.{Query, QueryHelpers, QueryOptimizer, Rogue, ShardingOk, !<:<}
import io.fsq.rogue.adapter.MongoClientAdapter
import io.fsq.rogue.types.MongoDisallowed
import scala.collection.generic.CanBuildFrom


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

  // TODO(jacob): Deprecate in favor of fetch, which can return arbitrary collection types.
  def fetchList[M <: MetaRecord, R <: Record, State](
    query: Query[M, R, State],
    readPreferenceOpt: Option[ReadPreference] = None
  )(
    implicit ev: ShardingOk[M, State],
    ev2: M !<:< MongoDisallowed
  ): Result[List[R]] = fetch(query, readPreferenceOpt)
}
