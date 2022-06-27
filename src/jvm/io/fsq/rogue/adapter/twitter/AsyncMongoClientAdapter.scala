// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.adapter.twitter

import com.mongodb.{DuplicateKeyException, ErrorCategory, MongoNamespace, MongoWriteException, ReadPreference}
import com.mongodb.bulk.BulkWriteResult
import com.mongodb.client.model.{
  BulkWriteOptions,
  CountOptions,
  FindOneAndDeleteOptions,
  FindOneAndUpdateOptions,
  IndexModel,
  ReplaceOptions,
  UpdateOptions,
  WriteModel
}
import com.mongodb.reactivestreams.client.{FindPublisher, MongoCollection, MongoDatabase}
import com.twitter.util.{Future, Try => TwitterTry}
import io.fsq.rogue.{Iter, Query, RogueException}
import io.fsq.rogue.adapter.{MongoClientAdapter, MongoCollectionFactory}
import io.fsq.rogue.util.QueryUtilities
import java.util.{List => JavaList}
import java.util.concurrent.TimeUnit
import org.bson.BsonValue
import org.bson.conversions.Bson
import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

object AsyncMongoClientAdapter {
  type CollectionFactory[
    DocumentValue,
    Document <: MongoClientAdapter.BaseDocument[DocumentValue],
    MetaRecord,
    Record
  ] = MongoCollectionFactory[
    MongoDatabase,
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
  Record
](
  collectionFactory: AsyncMongoClientAdapter.CollectionFactory[DocumentValue, Document, MetaRecord, Record],
  queryHelpers: QueryUtilities[Future]
) extends MongoClientAdapter[MongoDatabase, MongoCollection, DocumentValue, Document, MetaRecord, Record, Future](
    collectionFactory,
    queryHelpers
  ) {

  type Cursor = FindPublisher[Document]

  override protected def getCollectionNamespace(collection: MongoCollection[Document]): MongoNamespace = {
    collection.getNamespace
  }

  override def wrapResult[T](value: Try[T]): Future[T] = {
    Future.const(TwitterTry.fromScala(value))
  }

  // TODO(jacob): I THINK the new clients opt to throw write exceptions instead of
  //    DuplicateKeyExceptions and that catching those here is unnecessary. This is a
  //    difficult condition to test however, so we will have to wait and see what the
  //    stats measure.
  override protected def upsertWithDuplicateKeyRetry[T](upsert: => Future[T]): Future[T] = {
    upsert.rescue({
      case rogueException: RogueException =>
        Option(rogueException.getCause) match {
          case Some(_: DuplicateKeyException) => {
            queryHelpers.logger.logCounter("rogue.adapter.upsert.DuplicateKeyException")
            upsert
          }

          case Some(mwe: MongoWriteException) =>
            mwe.getError.getCategory match {
              case ErrorCategory.DUPLICATE_KEY => {
                queryHelpers.logger.logCounter("rogue.adapter.upsert.MongoWriteException-DUPLICATE_KEY")
                upsert
              }
              case ErrorCategory.EXECUTION_TIMEOUT | ErrorCategory.UNCATEGORIZED => {
                Future.exception(rogueException)
              }
            }

          case _ => Future.exception(rogueException)
        }
    })
  }

  override protected def runCommand[M <: MetaRecord, T](
    descriptionFunc: () => String,
    query: Query[M, _, _]
  )(
    f: => Future[T]
  ): Future[T] = {
    // Use nanoTime instead of currentTimeMillis to time the query since
    // currentTimeMillis only has 10ms granularity on many systems.
    val start = System.nanoTime
    val instanceName: String = collectionFactory.getInstanceNameFromQuery(query)

    // Note that it's expensive to call descriptionFunc, it does toString on the Query
    // the logger methods are call by name
    queryHelpers.logger
      .onExecuteQuery(query, instanceName, descriptionFunc(), f)
      .transform(resultTry => {
        val timeMs = (System.nanoTime - start) / 1000000
        queryHelpers.logger.log(query, instanceName, descriptionFunc(), timeMs)

        resultTry.asScala match {
          case Failure(exception: Exception) =>
            Future.exception(
              new RogueException(
                s"Mongo query on $instanceName [${descriptionFunc()}] failed after $timeMs ms",
                exception
              )
            )
          case _ => Future.const(resultTry)
        }
      })
  }

  override protected def countImpl(
    collection: MongoCollection[Document]
  )(
    filter: Bson,
    options: CountOptions
  ): Future[Long] = {
    TwitterAsyncUtil
      .optResult(collection.countDocuments(filter, options))
      .map({
        case None => 0
        case Some(javaLong) => javaLong: Long
      })
  }

  override protected def distinctImpl[T](
    resultAccessor: => T, // call by name
    accumulator: BsonValue => Unit
  )(
    collection: MongoCollection[Document]
  )(
    fieldName: String,
    filter: Bson
  ): Future[T] = {
    TwitterAsyncUtil
      .forEachResult(
        collection.distinct(fieldName, filter, classOf[BsonValue]),
        accumulator
      )
      .map(_ => resultAccessor)
  }

  override def explainImpl[M <: MetaRecord](
    database: MongoDatabase,
    command: Bson,
    readPreference: ReadPreference
  ): Future[Document] = {
    TwitterAsyncUtil.singleResult(database.runCommand(command, readPreference, collectionFactory.documentClass))
  }

  override protected def forEachProcessor[T](
    resultAccessor: => T, // call by name
    accumulator: Document => Unit
  )(
    cursor: Cursor
  ): Future[T] = {
    TwitterAsyncUtil.forEachResult(cursor, accumulator).map(_ => resultAccessor)
  }

  override protected def iterateProcessor[R, T](
    initialIterState: T,
    deserializer: Document => R,
    handler: (T, Iter.Event[R]) => Iter.Command[T]
  )(
    cursor: Cursor
  ): Future[T] = {
    @volatile var state = initialIterState
    val sub = new TwitterBaseSubscriber[Document, T] {
      override def onNext(t: Document): Unit = {
        Try(deserializer(t)) match {
          case Success(record) => {
            handler(state, Iter.OnNext(record)) match {
              case Iter.Continue(newIterState) => {
                state = newIterState
                subscription.request(1)
              }
              case Iter.Return(finalState) => {
                promise.setValue(finalState)
                subscription.cancel()
              }
            }
          }
          case Failure(t) => {
            subscription.cancel()
            onError(t)
          }
        }
      }

      override def onComplete(): Unit = {
        if (!promise.isDefined) {
          promise.setValue(handler(state, Iter.OnComplete).state)
        }
      }

      override def onError(throwable: Throwable): Unit = {
        throwable match {
          case e: Exception => promise.setValue(handler(state, Iter.OnError(e)).state)
          case _: Throwable => promise.setException(throwable)
        }
      }
    }
    cursor.subscribe(sub)
    sub.promise
  }

  override protected def findImpl[T](
    processor: Cursor => Future[T]
  )(
    collection: MongoCollection[Document]
  )(
    filter: Bson
  )(
    batchSizeOpt: Option[Int] = None,
    commentOpt: Option[String] = None,
    hintOpt: Option[Bson],
    limitOpt: Option[Int] = None,
    skipOpt: Option[Int] = None,
    sortOpt: Option[Bson] = None,
    projectionOpt: Option[Bson] = None,
    maxTimeMSOpt: Option[Long] = None
  ): Future[T] = {
    val cursor = collection.find(filter)

    // The default behavior in MongoIterableSubscription.java is to use
    // an underlying batch size of 2 unless more is requested from the
    // subscription. Setting it to 0 means to let the server decide an appropriate
    // batch size, which used to be the default of the driver.
    cursor.batchSize(batchSizeOpt.getOrElse(0))
    commentOpt.foreach(cursor.comment(_))
    hintOpt.foreach(cursor.hint(_))
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
  ): Future[R] = {
    TwitterAsyncUtil.unitResult(collection.insertOne(document)).map(_ => record)
  }

  override protected def insertAllImpl[R <: Record](
    collection: MongoCollection[Document]
  )(
    records: Seq[R],
    documents: Seq[Document]
  ): Future[Seq[R]] = {
    TwitterAsyncUtil.unitResult(collection.insertMany(documents.asJava)).map(_ => records)
  }

  override protected def replaceOneImpl[R <: Record](
    collection: MongoCollection[Document]
  )(
    record: R,
    filter: Bson,
    document: Document,
    options: ReplaceOptions
  ): Future[R] = {
    TwitterAsyncUtil.singleResult(collection.replaceOne(filter, document, options)).map(_ => record)
  }

  override protected def removeImpl[R <: Record](
    collection: MongoCollection[Document]
  )(
    record: R,
    document: Document
  ): Future[Long] = {
    TwitterAsyncUtil
      .singleResult(collection.deleteOne(document))
      .map(_.getDeletedCount)
  }

  override protected def deleteImpl(
    collection: MongoCollection[Document]
  )(
    filter: Bson
  ): Future[Long] = {
    TwitterAsyncUtil
      .singleResult(collection.deleteMany(filter))
      .map(_.getDeletedCount)
  }

  override protected def updateOneImpl(
    collection: MongoCollection[Document]
  )(
    filter: Bson,
    update: Bson,
    options: UpdateOptions
  ): Future[Long] = {
    TwitterAsyncUtil
      .singleResult(collection.updateOne(filter, update, options))
      .map(_.getModifiedCount)
  }

  override protected def updateManyImpl(
    collection: MongoCollection[Document]
  )(
    filter: Bson,
    update: Bson,
    options: UpdateOptions
  ): Future[Long] = {
    TwitterAsyncUtil
      .singleResult(collection.updateMany(filter, update, options))
      .map(_.getModifiedCount)
  }

  override protected def findOneAndUpdateImpl[R](
    deserializer: Document => R
  )(
    collection: MongoCollection[Document]
  )(
    filter: Bson,
    update: Bson,
    options: FindOneAndUpdateOptions
  ): Future[Option[R]] = {
    TwitterAsyncUtil
      .optResult(
        collection.findOneAndUpdate(filter, update, options)
      )
      .map(_.map(deserializer))
  }

  override protected def findOneAndDeleteImpl[R](
    deserializer: Document => R
  )(
    collection: MongoCollection[Document]
  )(
    filter: Bson,
    options: FindOneAndDeleteOptions
  ): Future[Option[R]] = {
    TwitterAsyncUtil
      .optResult(
        collection.findOneAndDelete(filter)
      )
      .map(_.map(deserializer))
  }

  override protected def createIndexesImpl(
    collection: MongoCollection[Document]
  )(
    indexes: Seq[IndexModel]
  ): Future[Seq[String]] = {
    val indexNames = Vector.newBuilder[String]
    TwitterAsyncUtil
      .forEachResult(
        collection.createIndexes(indexes.asJava),
        (indexName: String) => indexNames += indexName
      )
      .map(_ => indexNames.result())
  }

  override protected def bulkWriteImpl(
    collection: MongoCollection[Document]
  )(
    requests: JavaList[WriteModel[Document]],
    options: BulkWriteOptions
  ): Future[Option[BulkWriteResult]] = {
    TwitterAsyncUtil.optResult(collection.bulkWrite(requests, options))
  }
}
