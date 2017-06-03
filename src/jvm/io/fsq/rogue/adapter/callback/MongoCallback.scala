// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.adapter.callback

import com.mongodb.async.SingleResultCallback
import io.fsq.common.scala.Identity._
import scala.util.Try


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

  /** Wrap an empty result for a no-op query. This is included here to eliminate the need
    * to subclass AsyncMongoClientAdapter for new Result types.
    */
  def wrapResult[T](value: => T): Result[T]
}
