// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.adapter

import com.mongodb.Block
import com.mongodb.async.SingleResultCallback
import com.mongodb.async.client.MongoCollection
import com.mongodb.client.model.CountOptions
import io.fsq.common.scala.Identity._
import org.bson.conversions.Bson


object MongoCallback {
  trait Implicits {
    implicit def scalaLongToJavaLong[Result[_]](
      callback: MongoCallback[Result, Long]
    ): MongoCallback[Result, java.lang.Long] = {
      callback.asInstanceOf[MongoCallback[Result, java.lang.Long]]
    }
  }
}

trait MongoCallback[Result[_], T] extends SingleResultCallback[T] {

  def result: Result[T]

  protected def processResult(result: T): Unit
  protected def processThrowable(throwable: Throwable): Unit

  override def onResult(result: T, throwable: Throwable): Unit = {
    if (throwable !=? null) {
      processThrowable(throwable)
    } else {
      processResult(result)
    }
  }
}


trait MongoCallbackFactory[Result[_]] {
  def newCallback[T]: MongoCallback[Result, T]

  /** Wrap an empty result for a no-op query. This is included here to eliminate the need
    * to subclass AsyncMongoClientAdapter for new Result types.
    */
  def wrapEmptyResult[T](value: T): Result[T]
}


object AsyncMongoClientAdapter {
  type CollectionFactory[Document, MetaRecord, Record] = MongoCollectionFactory[
    MongoCollection,
    Document,
    MetaRecord,
    Record
  ]
}

class AsyncMongoClientAdapter[Document, MetaRecord, Record, Result[_]](
  collectionFactory: AsyncMongoClientAdapter.CollectionFactory[Document, MetaRecord, Record],
  callbackFactory: MongoCallbackFactory[Result]
) extends MongoClientAdapter[MongoCollection, Document, MetaRecord, Record, Result](
  collectionFactory
) with MongoCallback.Implicits {

  override def wrapEmptyResult[T](value: T): Result[T] = {
    callbackFactory.wrapEmptyResult(value)
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
    accumulator: Block[Document]
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
    collection.distinct(fieldName, filter, collectionFactory.documentClass).forEach(accumulator, queryCallback)
    resultCallback.result
  }
}
