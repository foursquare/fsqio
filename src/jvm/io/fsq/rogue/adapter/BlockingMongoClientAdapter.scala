// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.adapter

import com.mongodb.{DuplicateKeyException, ErrorCategory, MongoNamespace, MongoWriteException, ReadPreference}
import com.mongodb.bulk.BulkWriteResult
import com.mongodb.client.{FindIterable, MongoCollection, MongoDatabase}
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
import io.fsq.rogue.{Iter, Query, RogueException}
import io.fsq.rogue.util.QueryUtilities
import java.util.{List => JavaList}
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import org.bson.BsonValue
import org.bson.conversions.Bson
import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

object BlockingResult {
  trait Implicits {
    implicit def wrap[T](value: T): BlockingResult[T] = new BlockingResult[T](value)
    implicit def unwrap[T](wrapped: BlockingResult[T]): T = wrapped.unwrap
  }

  object Implicits extends Implicits
}

// TODO(jacob): Currently this can't be an AnyVal due to erasure causing a clash in the
//    wrapEmptyResult method below, see if there is a workaround.
class BlockingResult[T](val value: T) {
  def unwrap: T = value
}

object BlockingMongoClientAdapter {
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

class BlockingMongoClientAdapter[
  DocumentValue,
  Document <: MongoClientAdapter.BaseDocument[DocumentValue],
  MetaRecord,
  Record
](
  collectionFactory: BlockingMongoClientAdapter.CollectionFactory[DocumentValue, Document, MetaRecord, Record],
  queryHelpers: QueryUtilities[BlockingResult]
) extends MongoClientAdapter[
    MongoDatabase,
    MongoCollection,
    DocumentValue,
    Document,
    MetaRecord,
    Record,
    BlockingResult
  ](
    collectionFactory,
    queryHelpers
  )
  with BlockingResult.Implicits {

  type Cursor = FindIterable[Document]

  override def wrapResult[T](value: Try[T]): BlockingResult[T] = {
    new BlockingResult[T](value.get)
  }

  override protected def getCollectionNamespace(collection: MongoCollection[Document]): MongoNamespace = {
    collection.getNamespace
  }

  // TODO(jacob): I THINK the new clients opt to throw write exceptions instead of
  //    DuplicateKeyExceptions and that catching those here is unnecessary. This is a
  //    difficult condition to test however, so we will have to wait and see what the
  //    stats measure.
  override protected def upsertWithDuplicateKeyRetry[T](upsert: => BlockingResult[T]): BlockingResult[T] = {
    try {
      upsert
    } catch {
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
              case ErrorCategory.EXECUTION_TIMEOUT | ErrorCategory.UNCATEGORIZED => throw rogueException
            }

          case _ => throw rogueException
        }
    }
  }

  override protected def runCommand[M <: MetaRecord, T](
    descriptionFunc: () => String,
    query: Query[M, _, _]
  )(
    f: => BlockingResult[T]
  ): BlockingResult[T] = {
    // Use nanoTime instead of currentTimeMillis to time the query since
    // currentTimeMillis only has 10ms granularity on many systems.
    val start = System.nanoTime
    val instanceName: String = collectionFactory.getInstanceNameFromQuery(query)
    // Note that it's expensive to call descriptionFunc, it does toString on the Query
    // the logger methods are call by name
    try {
      queryHelpers.logger.onExecuteQuery(query, instanceName, descriptionFunc(), f)
    } catch {
      // we only encode Exceptions
      case e: Exception => {
        val timeMs = (System.nanoTime - start) / 1000000
        throw new RogueException(
          s"Mongo query on $instanceName [${descriptionFunc()}] failed after $timeMs ms",
          e
        )
      }
    } finally {
      queryHelpers.logger.log(query, instanceName, descriptionFunc(), (System.nanoTime - start) / 1000000)
    }
  }

  override protected def countImpl(
    collection: MongoCollection[Document]
  )(
    filter: Bson,
    options: CountOptions
  ): BlockingResult[Long] = {
    collection.countDocuments(filter, options)
  }

  // TODO(iant): Delete after Scala 2.12 upgrade with SAM support
  implicit class IterableForEach[T](iterable: java.lang.Iterable[T]) {
    def forEach(f: T => Unit): Unit = iterable.forEach(new Consumer[T] {
      override def accept(t: T): Unit = f(t)
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
  ): BlockingResult[T] = {
    collection.distinct(fieldName, filter, classOf[BsonValue]).forEach(accumulator)
    resultAccessor
  }

  override def explainImpl[M <: MetaRecord](
    database: MongoDatabase,
    command: Bson,
    readPreference: ReadPreference
  ): BlockingResult[Document] = {
    wrap(database.runCommand(command, readPreference, collectionFactory.documentClass))
  }

  override protected def forEachProcessor[T](
    resultAccessor: => T, // call by name
    accumulator: Document => Unit
  )(
    cursor: Cursor
  ): BlockingResult[T] = {
    cursor.forEach(accumulator)
    resultAccessor
  }

  override protected def iterateProcessor[R, T](
    initialIterState: T,
    deserializer: Document => R,
    handler: (T, Iter.Event[R]) => Iter.Command[T]
  )(
    cursor: Cursor
  ): BlockingResult[T] = {
    var iterState = initialIterState
    var continue = true
    val iterator = cursor.iterator

    while (continue) {
      if (iterator.hasNext) {
        Try(deserializer(iterator.next())) match {
          case Success(record) =>
            handler(iterState, Iter.OnNext(record)) match {
              case Iter.Continue(newIterState) => iterState = newIterState
              case Iter.Return(finalState) => {
                iterState = finalState
                continue = false
              }
            }
          case Failure(exception: Exception) => {
            iterState = handler(iterState, Iter.OnError(exception)).state
            continue = false
          }
          case Failure(throwable) => throw throwable
        }
      } else {
        iterState = handler(iterState, Iter.OnComplete).state
        continue = false
      }
    }

    iterState
  }

  override protected def findImpl[T](
    processor: Cursor => BlockingResult[T]
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
  ): BlockingResult[T] = {
    val cursor = collection.find(filter)

    batchSizeOpt.foreach(cursor.batchSize(_))
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
  ): BlockingResult[R] = {
    collection.insertOne(document)
    record
  }

  override protected def insertAllImpl[R <: Record](
    collection: MongoCollection[Document]
  )(
    records: Seq[R],
    documents: Seq[Document]
  ): BlockingResult[Seq[R]] = {
    collection.insertMany(documents.asJava)
    records
  }

  override protected def replaceOneImpl[R <: Record](
    collection: MongoCollection[Document]
  )(
    record: R,
    filter: Bson,
    document: Document,
    options: ReplaceOptions
  ): BlockingResult[R] = {
    collection.replaceOne(filter, document, options)
    record
  }

  override protected def removeImpl[R <: Record](
    collection: MongoCollection[Document]
  )(
    record: R,
    document: Document
  ): BlockingResult[Long] = {
    val deleteResult = collection.deleteOne(document)
    deleteResult.getDeletedCount
  }

  override protected def deleteImpl(
    collection: MongoCollection[Document]
  )(
    filter: Bson
  ): BlockingResult[Long] = {
    val deleteResult = collection.deleteMany(filter)
    deleteResult.getDeletedCount
  }

  override protected def updateOneImpl(
    collection: MongoCollection[Document]
  )(
    filter: Bson,
    update: Bson,
    options: UpdateOptions
  ): BlockingResult[Long] = {
    val updateResult = collection.updateOne(filter, update, options)
    updateResult.getModifiedCount
  }

  override protected def updateManyImpl(
    collection: MongoCollection[Document]
  )(
    filter: Bson,
    update: Bson,
    options: UpdateOptions
  ): BlockingResult[Long] = {
    val updateResult = collection.updateMany(filter, update, options)
    updateResult.getModifiedCount
  }

  override protected def findOneAndUpdateImpl[R](
    deserializer: Document => R
  )(
    collection: MongoCollection[Document]
  )(
    filter: Bson,
    update: Bson,
    options: FindOneAndUpdateOptions
  ): BlockingResult[Option[R]] = {
    val document = collection.findOneAndUpdate(filter, update, options)
    Option(document).map(deserializer)
  }

  override protected def findOneAndDeleteImpl[R](
    deserializer: Document => R
  )(
    collection: MongoCollection[Document]
  )(
    filter: Bson,
    options: FindOneAndDeleteOptions
  ): BlockingResult[Option[R]] = {
    val document = collection.findOneAndDelete(filter, options)
    Option(document).map(deserializer)
  }

  override protected def createIndexesImpl(
    collection: MongoCollection[Document]
  )(
    indexes: Seq[IndexModel]
  ): BlockingResult[Seq[String]] = {
    collection.createIndexes(indexes.asJava).asScala
  }

  override protected def bulkWriteImpl(
    collection: MongoCollection[Document]
  )(
    requests: JavaList[WriteModel[Document]],
    options: BulkWriteOptions
  ): BlockingResult[Option[BulkWriteResult]] = {
    Some(collection.bulkWrite(requests, options))
  }
}
