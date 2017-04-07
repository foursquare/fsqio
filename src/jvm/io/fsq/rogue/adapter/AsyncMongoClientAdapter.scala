// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.adapter

import com.mongodb.{Block, MongoNamespace}
import com.mongodb.async.SingleResultCallback
import com.mongodb.async.client.MongoCollection
import com.mongodb.client.model.CountOptions
import io.fsq.rogue.adapter.callback.{MongoCallback, MongoCallbackFactory}
import org.bson.BsonValue
import org.bson.conversions.Bson


object AsyncMongoClientAdapter {
  type CollectionFactory[
    DocumentValue,
    Document <: java.util.Map[String, DocumentValue],
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
  Document <: java.util.Map[String, DocumentValue],
  MetaRecord,
  Record,
  Result[_]
](
  collectionFactory: AsyncMongoClientAdapter.CollectionFactory[DocumentValue, Document, MetaRecord, Record],
  callbackFactory: MongoCallbackFactory[Result]
) extends MongoClientAdapter[MongoCollection, DocumentValue, Document, MetaRecord, Record, Result](
  collectionFactory
) with MongoCallback.Implicits {

  override def wrapEmptyResult[T](value: T): Result[T] = {
    callbackFactory.wrapEmptyResult(value)
  }

  override protected def getCollectionNamespace(collection: MongoCollection[Document]): MongoNamespace = {
    collection.getNamespace
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
}
