// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.adapter

import com.mongodb.async.SingleResultCallback
import com.mongodb.async.client.MongoCollection
import io.fsq.common.scala.Identity._


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
) {

  override def wrapEmptyResult[T](value: T): Result[T] = {
    callbackFactory.wrapEmptyResult(value)
  }
}
