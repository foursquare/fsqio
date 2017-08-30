// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.adapter

import com.mongodb.{Block, DuplicateKeyException, ErrorCategory, MongoNamespace, MongoWriteException}
import com.mongodb.async.{AsyncBatchCursor, SingleResultCallback}
import com.mongodb.async.client.{FindIterable, MongoCollection}
import com.mongodb.client.model.{CountOptions, FindOneAndDeleteOptions, FindOneAndUpdateOptions, UpdateOptions}
import com.mongodb.client.result.{DeleteResult, UpdateResult}
import io.fsq.common.scala.Identity._
import io.fsq.rogue.{Iter, Query, RogueException}
import io.fsq.rogue.adapter.callback.{MongoCallback, MongoCallbackFactory}
import io.fsq.rogue.util.QueryUtilities
import java.util.{List => JavaList}
import java.util.concurrent.TimeUnit
import org.bson.BsonValue
import org.bson.conversions.Bson
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.util.{Failure, Success, Try}


object AsyncMongoClientAdapter {
  type CollectionFactory[
    DocumentValue,
    Document <: MongoClientAdapter.BaseDocument[DocumentValue],
    MetaRecord,
    Record
  ] = MongoCollectionFactory[
    MongoCollection,
    DocumentValue,
    Document,
    MetaRecord,
    Record
  ]
}

class AsyncMongoClientAdapter[
  DocumentValue,
  Document <: MongoClientAdapter.BaseDocument[DocumentValue],
  MetaRecord,
  Record,
  Result[_]
](
  collectionFactory: AsyncMongoClientAdapter.CollectionFactory[DocumentValue, Document, MetaRecord, Record],
  callbackFactory: MongoCallbackFactory[Result],
  queryHelpers: QueryUtilities[Result]
) extends MongoClientAdapter[MongoCollection, DocumentValue, Document, MetaRecord, Record, Result](
  collectionFactory,
  queryHelpers
) with MongoCallback.Implicits {

  type Cursor = FindIterable[Document]

  override def wrapResult[T](value: => T): Result[T] = {
    callbackFactory.wrapResult(value)
  }

  override protected def getCollectionNamespace(collection: MongoCollection[Document]): MongoNamespace = {
    collection.getNamespace
  }

  // TODO(jacob): I THINK the new clients opt to throw write exceptions instead of
  //    DuplicateKeyExceptions and that catching those here is unnecessary. This is a
  //    difficult condition to test however, so we will have to wait and see what the
  //    stats measure.
  override protected def upsertWithDuplicateKeyRetry[T](upsert: => Result[T]): Result[T] = {
    callbackFactory.transformResult[T, T](
      upsert,
      _ match {
        case Success(value) => wrapResult(value)

        case Failure(rogueException: RogueException) => Option(rogueException.getCause) match {
          case Some(_: DuplicateKeyException) => {
            queryHelpers.logger.logCounter("rogue.adapter.upsert.DuplicateKeyException")
            upsert
          }

          case Some(mwe: MongoWriteException) => mwe.getError.getCategory match {
            case ErrorCategory.DUPLICATE_KEY => {
              queryHelpers.logger.logCounter("rogue.adapter.upsert.MongoWriteException-DUPLICATE_KEY")
              upsert
            }
            case ErrorCategory.EXECUTION_TIMEOUT | ErrorCategory.UNCATEGORIZED => throw rogueException
          }

          case _ => wrapResult(throw rogueException)
        }

        case Failure(other) => wrapResult(throw other)
      }
    )
  }

  override protected def runCommand[M <: MetaRecord, T](
    descriptionFunc: () => String,
    query: Query[M, _, _]
  )(
    f: => Result[T]
  ): Result[T] = {
    // Use nanoTime instead of currentTimeMillis to time the query since
    // currentTimeMillis only has 10ms granularity on many systems.
    val start = System.nanoTime
    val instanceName: String = collectionFactory.getInstanceNameFromQuery(query)

    // Note that it's expensive to call descriptionFunc, it does toString on the Query
    // the logger methods are call by name
    callbackFactory.transformResult[T, T](
      queryHelpers.logger.onExecuteQuery(query, instanceName, descriptionFunc(), f),
      resultTry => {
        val timeMs = (System.nanoTime - start) / 1000000
        queryHelpers.logger.log(query, instanceName, descriptionFunc(), timeMs)

        resultTry match {
          case Success(value) => wrapResult(value)
          case Failure(exception: Exception) => wrapResult(
            throw new RogueException(
              s"Mongo query on $instanceName [${descriptionFunc()}] failed after $timeMs ms",
              exception
            )
          )
          case Failure(other) => wrapResult(throw other)  // we only encode Exceptions
        }
      }
    )
  }

  override protected def countImpl(
    collection: MongoCollection[Document]
  )(
    filter: Bson,
    options: CountOptions
  ): Result[Long] = {
    val callback = callbackFactory.newCallback[Long]
    collection.count(filter, options, callback)
    callback.result
  }

  override protected def distinctImpl[T](
    resultAccessor: => T, // call by name
    accumulator: Block[BsonValue]
  )(
    collection: MongoCollection[Document]
  )(
    fieldName: String,
    filter: Bson
  ): Result[T] = {
    val resultCallback = callbackFactory.newCallback[T]
    val queryCallback = new SingleResultCallback[Void] {
      override def onResult(result: Void, throwable: Throwable): Unit = {
        resultCallback.onResult(resultAccessor, throwable)
      }
    }
    collection.distinct(fieldName, filter, classOf[BsonValue]).forEach(accumulator, queryCallback)
    resultCallback.result
  }

  override protected def forEachProcessor[T](
    resultAccessor: => T, // call by name
    accumulator: Block[Document]
  )(
    cursor: Cursor
  ): Result[T] = {
    val resultCallback = callbackFactory.newCallback[T]
    val queryCallback = new SingleResultCallback[Void] {
      override def onResult(result: Void, throwable: Throwable): Unit = {
        resultCallback.onResult(resultAccessor, throwable)
      }
    }

    cursor.forEach(accumulator, queryCallback)
    resultCallback.result
  }

  private def newIterateCursorCallback[R <: Record, T](
    initialIterState: T,
    deserializer: Document => R,
    handler: (T, Iter.Event[R]) => Iter.Command[T]
  )(
    resultCallback: SingleResultCallback[T],
    batchCursor: AsyncBatchCursor[Document]
  ): SingleResultCallback[JavaList[Document]] = new SingleResultCallback[JavaList[Document]] {
    @volatile var iterState = initialIterState

    override def onResult(maybeBatch: JavaList[Document], throwable: Throwable): Unit = {
      (Option(maybeBatch), Option(throwable)) match {
        case (_, Some(exception: Exception)) => {
          resultCallback.onResult(handler(iterState, Iter.Error(exception)).state, null)
        }
        case (_, Some(throwable)) => resultCallback.onResult(iterState, throwable)

        case (None, None) => resultCallback.onResult(handler(iterState, Iter.EOF).state, null)

        case (Some(batch), None) => {
          var continue = true
          val iterator = batch.iterator

          while (continue && iterator.hasNext) {
            Try(deserializer(iterator.next())) match {
              case Success(record) => handler(iterState, Iter.Item(record)) match {
                case Iter.Continue(newIterState) => iterState = newIterState
                case Iter.Return(finalState) => {
                  resultCallback.onResult(finalState, null)
                  continue = false
                }
              }
              case Failure(exception: Exception) => {
                resultCallback.onResult(handler(iterState, Iter.Error(exception)).state, null)
                continue = false
              }
              case Failure(throwable) => {
                resultCallback.onResult(iterState, throwable)
                continue = false
              }
            }
          }

          if (continue) {
            batchCursor.next(this)
          }
        }
      }
    }
  }

  private def baseIterationProcessor[CursorResult, R <: Record, T](
    initialIterState: T,
    handler: (T, Iter.Event[R]) => Iter.Command[T],
    cursorCallback: (SingleResultCallback[T], AsyncBatchCursor[Document]) => SingleResultCallback[JavaList[Document]]
  )(
    cursor: Cursor
  ): Result[T] = {
    val resultCallback = callbackFactory.newCallback[T]

    val queryCallback = new SingleResultCallback[AsyncBatchCursor[Document]] {
      override def onResult(batchCursor: AsyncBatchCursor[Document], throwable: Throwable): Unit = {
        Option(throwable) match {
          case None => batchCursor.next(cursorCallback(resultCallback, batchCursor))
          case Some(exception: Exception) => {
            resultCallback.onResult(handler(initialIterState, Iter.Error(exception)).state, null)
          }
          case Some(throwable) => resultCallback.onResult(initialIterState, throwable)
        }
      }
    }

    cursor.batchCursor(queryCallback)
    resultCallback.result
  }

  override protected def iterateProcessor[R <: Record, T](
    initialIterState: T,
    deserializer: Document => R,
    handler: (T, Iter.Event[R]) => Iter.Command[T]
  )(
    cursor: Cursor
  ): Result[T] = {
    baseIterationProcessor(
      initialIterState,
      handler,
      newIterateCursorCallback(initialIterState, deserializer, handler)
    )(
      cursor
    )
  }

  override protected def findImpl[T](
    processor: Cursor => Result[T]
  )(
    collection: MongoCollection[Document]
  )(
    filter: Bson
  )(
    modifiers: Bson,
    batchSizeOpt: Option[Int] = None,
    limitOpt: Option[Int] = None,
    skipOpt: Option[Int] = None,
    sortOpt: Option[Bson] = None,
    projectionOpt: Option[Bson] = None,
    maxTimeMSOpt: Option[Long] = None
  ): Result[T] = {
    val cursor = collection.find(filter)

    cursor.modifiers(modifiers)
    batchSizeOpt.foreach(cursor.batchSize(_))
    limitOpt.foreach(cursor.limit(_))
    skipOpt.foreach(cursor.skip(_))
    sortOpt.foreach(cursor.sort(_))
    projectionOpt.foreach(cursor.projection(_))
    maxTimeMSOpt.foreach(cursor.maxTime(_, TimeUnit.MILLISECONDS))

    processor(cursor)
  }

  override protected def insertImpl[R <: Record](
    collection: MongoCollection[Document]
  )(
    record: R,
    document: Document
  ): Result[R] = {
    val resultCallback = callbackFactory.newCallback[R]
    val queryCallback = new SingleResultCallback[Void] {
      override def onResult(result: Void, throwable: Throwable): Unit = {
        resultCallback.onResult(record, throwable)
      }
    }
    collection.insertOne(document, queryCallback)
    resultCallback.result
  }

  override protected def insertAllImpl[R <: Record](
    collection: MongoCollection[Document]
  )(
    records: Seq[R],
    documents: Seq[Document]
  ): Result[Seq[R]] = {
    val resultCallback = callbackFactory.newCallback[Seq[R]]
    val queryCallback = new SingleResultCallback[Void] {
      override def onResult(result: Void, throwable: Throwable): Unit = {
        resultCallback.onResult(records, throwable)
      }
    }
    collection.insertMany(documents.asJava, queryCallback)
    resultCallback.result
  }

  override protected def replaceOneImpl[R <: Record](
    collection: MongoCollection[Document]
  )(
    record: R,
    filter: Bson,
    document: Document,
    options: UpdateOptions
  ): Result[R] = {
    val resultCallback = callbackFactory.newCallback[R]
    val queryCallback = new SingleResultCallback[UpdateResult] {
      override def onResult(deleteResult: UpdateResult, throwable: Throwable): Unit = {
        resultCallback.onResult(record, throwable)
      }
    }
    collection.replaceOne(filter, document, options, queryCallback)
    resultCallback.result
  }

  override protected def removeImpl[R <: Record](
    collection: MongoCollection[Document]
  )(
    record: R,
    document: Document
  ): Result[Long] = {
    val resultCallback = callbackFactory.newCallback[Long]
    val queryCallback = new SingleResultCallback[DeleteResult] {
      override def onResult(deleteResult: DeleteResult, throwable: Throwable): Unit = {
        if (throwable !=? null) {
          resultCallback.onResult(0L, throwable)
        } else {
          resultCallback.onResult(deleteResult.getDeletedCount, throwable)
        }
      }
    }
    collection.deleteOne(document, queryCallback)
    resultCallback.result
  }

  override protected def deleteImpl(
    collection: MongoCollection[Document]
  )(
    filter: Bson
  ): Result[Long] = {
    val resultCallback = callbackFactory.newCallback[Long]
    val queryCallback = new SingleResultCallback[DeleteResult] {
      override def onResult(deleteResult: DeleteResult, throwable: Throwable): Unit = {
        if (throwable !=? null) {
          resultCallback.onResult(0L, throwable)
        } else {
          resultCallback.onResult(deleteResult.getDeletedCount, throwable)
        }
      }
    }
    collection.deleteMany(filter, queryCallback)
    resultCallback.result
  }

  override protected def updateOneImpl(
    collection: MongoCollection[Document]
  )(
    filter: Bson,
    update: Bson,
    options: UpdateOptions
  ): Result[Long] = {
    val resultCallback = callbackFactory.newCallback[Long]
    val queryCallback = new SingleResultCallback[UpdateResult] {
      override def onResult(updateResult: UpdateResult, throwable: Throwable): Unit = {
        if (throwable !=? null) {
          resultCallback.onResult(0L, throwable)
        } else {
          resultCallback.onResult(updateResult.getModifiedCount, throwable)
        }
      }
    }
    collection.updateOne(filter, update, options, queryCallback)
    resultCallback.result
  }

  override protected def updateManyImpl(
    collection: MongoCollection[Document]
  )(
    filter: Bson,
    update: Bson,
    options: UpdateOptions
  ): Result[Long] = {
    val resultCallback = callbackFactory.newCallback[Long]
    val queryCallback = new SingleResultCallback[UpdateResult] {
      override def onResult(updateResult: UpdateResult, throwable: Throwable): Unit = {
        if (throwable !=? null) {
          resultCallback.onResult(0L, throwable)
        } else {
          resultCallback.onResult(updateResult.getModifiedCount, throwable)
        }
      }
    }
    collection.updateMany(filter, update, options, queryCallback)
    resultCallback.result
  }

  override protected def findOneAndUpdateImpl[R <: Record](
    deserializer: Document => R
  )(
    collection: MongoCollection[Document]
  )(
    filter: Bson,
    update: Bson,
    options: FindOneAndUpdateOptions
  ): Result[Option[R]] = {
    val resultCallback = callbackFactory.newCallback[Option[R]]
    val queryCallback = new SingleResultCallback[Document] {
      override def onResult(document: Document, throwable: Throwable): Unit = {
        if (throwable !=? null) {
          resultCallback.onResult(null, throwable)
        } else {
          resultCallback.onResult(Option(document).map(deserializer), throwable)
        }
      }
    }
    collection.findOneAndUpdate(filter, update, options, queryCallback)
    resultCallback.result
  }

  override protected def findOneAndDeleteImpl[R <: Record](
    deserializer: Document => R
  )(
    collection: MongoCollection[Document]
  )(
    filter: Bson,
    options: FindOneAndDeleteOptions
  ): Result[Option[R]] = {
    val resultCallback = callbackFactory.newCallback[Option[R]]
    val queryCallback = new SingleResultCallback[Document] {
      override def onResult(document: Document, throwable: Throwable): Unit = {
        if (throwable !=? null) {
          resultCallback.onResult(null, throwable)
        } else {
          resultCallback.onResult(Option(document).map(deserializer), throwable)
        }
      }
    }
    collection.findOneAndDelete(filter, queryCallback)
    resultCallback.result
  }
}
