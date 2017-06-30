// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.adapter

import com.mongodb.{Block, DuplicateKeyException, ErrorCategory, MongoNamespace, MongoWriteException}
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.{CountOptions, UpdateOptions}
import io.fsq.rogue.{Query, RogueException}
import io.fsq.rogue.util.QueryUtilities
import java.util.concurrent.TimeUnit
import org.bson.BsonValue
import org.bson.conversions.Bson
import scala.collection.JavaConverters.seqAsJavaListConverter


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
) extends MongoClientAdapter[MongoCollection, DocumentValue, Document, MetaRecord, Record, BlockingResult](
  collectionFactory,
  queryHelpers
) with BlockingResult.Implicits {

  override def wrapResult[T](value: => T): BlockingResult[T] = new BlockingResult[T](value)

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
      case rogueException: RogueException => Option(rogueException.getCause) match {
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
    collection.count(filter, options)
  }

  override protected def distinctImpl[T](
    resultAccessor: => T, // call by name
    accumulator: Block[BsonValue]
  )(
    collection: MongoCollection[Document]
  )(
    fieldName: String,
    filter: Bson
  ): BlockingResult[T] = {
    collection.distinct(fieldName, filter, classOf[BsonValue]).forEach(accumulator)
    resultAccessor
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
  ): BlockingResult[T] = {
    val cursor = collection.find(filter)

    cursor.modifiers(modifiers)
    batchSizeOpt.foreach(cursor.batchSize(_))
    limitOpt.foreach(cursor.limit(_))
    skipOpt.foreach(cursor.skip(_))
    sortOpt.foreach(cursor.sort(_))
    projectionOpt.foreach(cursor.projection(_))
    maxTimeMSOpt.foreach(cursor.maxTime(_, TimeUnit.MILLISECONDS))

    cursor.forEach(accumulator)
    resultAccessor
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
}
