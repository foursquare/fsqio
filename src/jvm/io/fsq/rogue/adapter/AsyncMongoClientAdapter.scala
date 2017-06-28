// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.adapter

import com.mongodb.{Block, MongoNamespace}
import com.mongodb.async.SingleResultCallback
import com.mongodb.async.client.MongoCollection
import com.mongodb.client.model.{CountOptions, UpdateOptions}
import com.mongodb.client.result.{DeleteResult, UpdateResult}
import io.fsq.common.scala.Identity._
import io.fsq.rogue.{Query, RogueException}
import io.fsq.rogue.adapter.callback.{MongoCallback, MongoCallbackFactory}
import io.fsq.rogue.util.QueryUtilities
import java.util.concurrent.TimeUnit
import org.bson.BsonValue
import org.bson.conversions.Bson
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.util.{Failure, Success}


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

  override def wrapResult[T](value: => T): Result[T] = {
    callbackFactory.wrapResult(value)
  }

  override protected def getCollectionNamespace(collection: MongoCollection[Document]): MongoNamespace = {
    collection.getNamespace
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

  override protected def findImpl[T](
    resultAccessor: => T, // call by name
    accumulator: Block[Document]
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

    val resultCallback = callbackFactory.newCallback[T]
    val queryCallback = new SingleResultCallback[Void] {
      override def onResult(result: Void, throwable: Throwable): Unit = {
        resultCallback.onResult(resultAccessor, throwable)
      }
    }

    cursor.forEach(accumulator, queryCallback)
    resultCallback.result
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
}
