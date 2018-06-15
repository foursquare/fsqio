// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.adapter.callback

import com.mongodb.async.SingleResultCallback
import io.fsq.common.scala.Identity._
import scala.util.{Failure, Success, Try}

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

  protected def processResult(value: T): Unit
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

  def transformResult[T, U](result: Result[T], f: Try[T] => Result[U]): Result[U]

  // These implementations for map and flatMap in terms of transform are derived from the implementations
  // in Twitter's Future:
  // https://github.com/twitter/util/blob/develop/util-core/src/main/scala/com/twitter/util/Future.scala
  def mapResult[T, U](result: Result[T], f: T => U): Result[U] = {
    transformResult(result, (resultTry: Try[T]) => {
      resultTry match {
        case Success(r) => wrapResult(f(r))
        case Failure(e) => wrapException(e)
      }
    })
  }

  def flatMapResult[T, U](result: Result[T], f: T => Result[U]): Result[U] = {
    transformResult(result, (resultTry: Try[T]) => {
      resultTry match {
        case Success(r) => f(r)
        case Failure(e) => wrapException(e)
      }
    })
  }

  /** Wrap an empty result for a no-op query. This is included here to eliminate the need
    * to subclass AsyncMongoClientAdapter for new Result types.
    */
  def wrapResult[T](value: => T): Result[T]

  def wrapException[T](e: Throwable): Result[T]
}
