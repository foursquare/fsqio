// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.adapter.callback.twitter

import com.twitter.util.{Future, Promise}
import io.fsq.rogue.adapter.callback.{MongoCallback, MongoCallbackFactory}


class TwitterFutureMongoCallback[T] extends MongoCallback[Future, T] {

  private val promise = new Promise[T]

  override def result: Future[T] = promise

  override protected def processResult(value: T): Unit = {
    promise.setValue(value)
  }

  override protected def processThrowable(throwable: Throwable): Unit = {
    promise.setException(throwable)
  }
}

class TwitterFutureMongoCallbackFactory extends MongoCallbackFactory[Future] {

  override def newCallback[T]: TwitterFutureMongoCallback[T] = {
    new TwitterFutureMongoCallback[T]
  }

  override def wrapEmptyResult[T](value: T): Future[T] = Future.value(value)
}
