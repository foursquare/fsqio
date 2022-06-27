// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.query

import com.mongodb.{ReadPreference, WriteConcern}
import io.fsq.field.Field
import io.fsq.rogue.{
  !<:<,
  AddLimit,
  BulkInsertOne,
  BulkModifyQueryOperation,
  BulkOperation,
  BulkQueryOperation,
  FindAndModifyQuery,
  Iter,
  ModifyQuery,
  Query,
  QueryOptimizer,
  RequireShardKey,
  Required,
  Rogue,
  ShardingOk,
  Unlimited,
  Unselected,
  Unskipped
}
import com.mongodb.bulk.BulkWriteResult
import io.fsq.rogue.adapter.MongoClientAdapter
import io.fsq.rogue.index.UntypedMongoIndex
import io.fsq.rogue.types.MongoDisallowed
import scala.collection.generic.CanBuildFrom
import scala.collection.mutable.ArrayBuffer
import scala.util.Success

/** TODO(jacob): All of the collection methods implemented here should get rid of the
  *     option to send down a read preference, and just use the one on the query.
  */
class QueryExecutor[
  MongoDatabase,
  MongoCollection[_],
  DocumentValue,
  Document <: MongoClientAdapter.BaseDocument[DocumentValue],
  MetaRecord,
  Record,
  Result[_]
](
  adapter: MongoClientAdapter[MongoDatabase, MongoCollection, DocumentValue, Document, MetaRecord, Record, Result],
  optimizer: QueryOptimizer,
  serializer: RogueSerializer[MetaRecord, Record, Document]
) extends Rogue {

  def defaultWriteConcern: WriteConcern = adapter.queryHelpers.config.defaultWriteConcern

  def createIndexes[M <: MetaRecord](
    metaRecord: M
  )(
    indexes: UntypedMongoIndex*
  ): Result[Seq[String]] = {
    adapter.createIndexes(metaRecord)(indexes: _*)
  }

  def count[M <: MetaRecord, State](
    query: Query[M, _, State],
    readPreferenceOpt: Option[ReadPreference] = None
  )(
    implicit ev: ShardingOk[M, State],
    ev2: M !<:< MongoDisallowed
  ): Result[Long] = {
    if (optimizer.isEmptyQuery(query)) {
      adapter.wrapResult(Success(0L))
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
      adapter.wrapResult(Success(0L))
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
    implicit ev: ShardingOk[M, State],
    ev2: M !<:< MongoDisallowed
  ): Result[Seq[FieldType]] = {
    if (optimizer.isEmptyQuery(query)) {
      adapter.wrapResult(Success(Vector.empty[FieldType]))
    } else {
      adapter.distinct(query, field(query.meta).name, resultTransformer, readPreferenceOpt)
    }
  }

  def explain[M <: MetaRecord, State](
    query: Query[M, _, State],
    readPreferenceOpt: Option[ReadPreference] = None
  )(
    implicit ev: ShardingOk[M, State],
    ev2: M !<:< MongoDisallowed
  ): Result[Document] = {
    adapter.explain(query, readPreferenceOpt)
  }

  // TODO(jacob): Checking the MongoDisallowed trait against the record itself is broken.
  //    These insertion methods need to take a meta record type parameter as well.
  def save[R <: Record](
    record: R,
    writeConcern: WriteConcern = defaultWriteConcern
  )(
    implicit ev: R !<:< MongoDisallowed
  ): Result[R] = {
    adapter.save(record, serializer.writeToDocument(record), Some(writeConcern))
  }

  def insert[R <: Record](
    record: R,
    writeConcern: WriteConcern = defaultWriteConcern
  )(
    implicit ev: R !<:< MongoDisallowed
  ): Result[R] = {
    adapter.insert(record, serializer.writeToDocument(record), Some(writeConcern))
  }

  def insertAll[R <: Record](
    records: Seq[R],
    writeConcern: WriteConcern = defaultWriteConcern
  )(
    implicit ev: R !<:< MongoDisallowed
  ): Result[Seq[R]] = {
    adapter.insertAll(
      records,
      records.map(serializer.writeToDocument(_)),
      Some(writeConcern)
    )
  }

  def fetch[M <: MetaRecord, R, State, Collection[_]](
    query: Query[M, R, State],
    readPreferenceOpt: Option[ReadPreference] = None
  )(
    implicit ev: ShardingOk[M, State],
    ev2: M !<:< MongoDisallowed,
    canBuildFrom: CanBuildFrom[_, R, Collection[R]]
  ): Result[Collection[R]] = {
    val resultsBuilder = canBuildFrom()

    if (optimizer.isEmptyQuery(query)) {
      adapter.wrapResult(Success(resultsBuilder.result()))
    } else {
      def processor(document: Document): Unit = {
        resultsBuilder += serializer.readFromDocument(query.meta, query.select)(document)
      }
      adapter.query(resultsBuilder.result(), processor)(query, None, readPreferenceOpt)
    }
  }

  def fetchOne[M <: MetaRecord, R, State, LimitState](
    query: Query[M, R, State],
    readPreferenceOpt: Option[ReadPreference] = None
  )(
    implicit ev: AddLimit[State, LimitState],
    ev2: ShardingOk[M, LimitState],
    ev3: M !<:< MongoDisallowed
  ): Result[Option[R]] = {
    if (optimizer.isEmptyQuery(query)) {
      adapter.wrapResult(Success(None))
    } else {
      var result: Option[R] = None
      def processor(document: Document): Unit = {
        result = Some(serializer.readFromDocument(query.meta, query.select)(document))
      }
      adapter.query(result, processor)(query.limit(1), None, readPreferenceOpt)
    }
  }

  def foreach[M <: MetaRecord, R, State](
    query: Query[M, R, State],
    readPreferenceOpt: Option[ReadPreference] = None
  )(
    f: R => Unit
  )(
    implicit ev: ShardingOk[M, State],
    ev2: M !<:< MongoDisallowed
  ): Result[Unit] = {
    if (optimizer.isEmptyQuery(query)) {
      adapter.wrapResult(Success(()))
    } else {
      def processor(document: Document): Unit = {
        f(serializer.readFromDocument(query.meta, query.select)(document))
      }
      adapter.query((), processor)(query, None, readPreferenceOpt)
    }
  }

  def fetchBatch[M <: MetaRecord, R, State, Collection[_], T](
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
      adapter.wrapResult(Success(resultsBuilder.result()))
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

  def remove[R <: Record](
    record: R,
    writeConcern: WriteConcern = defaultWriteConcern
  ): Result[Long] = {
    adapter.remove(record, serializer.writeToDocument(record), Some(writeConcern))
  }

  // TODO(jacob): The exclamation marks here are dumb, remove them.
  def bulkDelete_!![M <: MetaRecord, State](
    query: Query[M, _, State],
    writeConcern: WriteConcern = defaultWriteConcern
  )(
    implicit ev: Required[State, Unselected with Unlimited with Unskipped],
    ev2: ShardingOk[M, State],
    ev3: M !<:< MongoDisallowed
  ): Result[Long] = {
    if (optimizer.isEmptyQuery(query)) {
      adapter.wrapResult(Success(0L))
    } else {
      adapter.delete(query, Some(writeConcern))
    }
  }

  def updateOne[M <: MetaRecord, State](
    query: ModifyQuery[M, State],
    writeConcern: WriteConcern = defaultWriteConcern
  )(
    implicit ev: RequireShardKey[M, State],
    ev2: M !<:< MongoDisallowed
  ): Result[Long] = {
    if (optimizer.isEmptyQuery(query)) {
      adapter.wrapResult(Success(0L))
    } else {
      adapter.modify(query, upsert = false, multi = false, Some(writeConcern))
    }
  }

  def updateMany[M <: MetaRecord, State](
    query: ModifyQuery[M, State],
    writeConcern: WriteConcern = defaultWriteConcern
  )(
    implicit ev: M !<:< MongoDisallowed
  ): Result[Long] = {
    if (optimizer.isEmptyQuery(query)) {
      adapter.wrapResult(Success(0L))
    } else {
      adapter.modify(query, upsert = false, multi = true, Some(writeConcern))
    }
  }

  def upsertOne[M <: MetaRecord, State](
    query: ModifyQuery[M, State],
    writeConcern: WriteConcern = defaultWriteConcern
  )(
    implicit ev: RequireShardKey[M, State],
    ev2: M !<:< MongoDisallowed
  ): Result[Long] = {
    if (optimizer.isEmptyQuery(query)) {
      adapter.wrapResult(Success(0L))
    } else {
      adapter.modify(query, upsert = true, multi = false, Some(writeConcern))
    }
  }

  def findAndUpdateOne[M <: MetaRecord, R](
    query: FindAndModifyQuery[M, R],
    returnNew: Boolean = false,
    writeConcern: WriteConcern = defaultWriteConcern
  )(
    implicit ev: M !<:< MongoDisallowed
  ): Result[Option[R]] = {
    if (optimizer.isEmptyQuery(query)) {
      adapter.wrapResult(Success(None))
    } else {
      val deserializer = serializer.readFromDocument(query.query.meta, query.query.select)(_)
      adapter.findOneAndUpdate(deserializer)(query, returnNew = returnNew, upsert = false, Some(writeConcern))
    }
  }

  def findAndUpsertOne[M <: MetaRecord, R](
    query: FindAndModifyQuery[M, R],
    returnNew: Boolean = false,
    writeConcern: WriteConcern = defaultWriteConcern
  )(
    implicit ev: M !<:< MongoDisallowed
  ): Result[Option[R]] = {
    if (optimizer.isEmptyQuery(query)) {
      adapter.wrapResult(Success(None))
    } else {
      val deserializer = serializer.readFromDocument(query.query.meta, query.query.select)(_)
      adapter.findOneAndUpdate(deserializer)(query, returnNew = returnNew, upsert = true, Some(writeConcern))
    }
  }

  def findAndDeleteOne[M <: MetaRecord, R, State](
    query: Query[M, R, State],
    writeConcern: WriteConcern = defaultWriteConcern
  )(
    implicit ev: RequireShardKey[M, State],
    ev2: M !<:< MongoDisallowed
  ): Result[Option[R]] = {
    if (optimizer.isEmptyQuery(query)) {
      adapter.wrapResult(Success(None))
    } else {
      val deserializer = serializer.readFromDocument(query.meta, query.select)(_)
      adapter.findOneAndDelete(deserializer)(query, Some(writeConcern))
    }
  }

  def iterate[M <: MetaRecord, R, State, T](
    query: Query[M, R, State],
    initialIterState: T,
    readPreferenceOpt: Option[ReadPreference] = None,
    batchSizeOpt: Option[Int] = None
  )(
    handler: (T, Iter.Event[R]) => Iter.Command[T]
  )(
    implicit ev: ShardingOk[M, State],
    ev2: M !<:< MongoDisallowed
  ): Result[T] = {
    if (optimizer.isEmptyQuery(query)) {
      adapter.wrapResult(Success(handler(initialIterState, Iter.OnComplete).state))
    } else {
      val deserializer = serializer.readFromDocument(query.meta, query.select)(_)
      adapter.iterate(query, initialIterState, deserializer, readPreferenceOpt, batchSizeOpt)(handler)
    }
  }

  def bulk[M <: MetaRecord, R <: Record](
    ops: Seq[BulkOperation[M, R]],
    ordered: Boolean = false,
    writeConcern: WriteConcern = defaultWriteConcern
  ): Result[Option[BulkWriteResult]] = {
    val nonEmptyOps = ops.filterNot({
      case _: BulkInsertOne[M, R] => false
      case BulkQueryOperation(query) => optimizer.isEmptyQuery(query)
      case BulkModifyQueryOperation(modifyQuery, _) => optimizer.isEmptyQuery(modifyQuery)
    })
    if (nonEmptyOps.isEmpty) {
      adapter.wrapResult(Success(None))
    } else {
      adapter.bulk(serializer.writeToDocument[R])(nonEmptyOps, ordered, Some(writeConcern))
    }
  }
}
