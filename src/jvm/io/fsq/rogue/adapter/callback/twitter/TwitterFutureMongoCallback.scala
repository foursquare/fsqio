// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.adapter.callback.twitter

import com.mongodb.MongoInterruptedException
import com.twitter.util.{Future, Promise, Return, Throw}
import io.fsq.rogue.RogueException
import io.fsq.rogue.adapter.callback.{MongoCallback, MongoCallbackFactory}
import java.lang.{StringBuilder => JavaStringBuilder}
import scala.util.{Failure, Success, Try}

class TwitterFutureMongoCallback[T] extends MongoCallback[Future, T] {

  private val promise = new Promise[T]

  override def result: Future[T] = promise

  override protected def processResult(value: T): Unit = {
    promise.setValue(value)
  }

  /** NOTE(jacob): There are a couple bits of magic happening here to make allocating
    *   this string as unnecessarily micro-optimized as possible:
    *
    *     - magic number 172: computed in the repl as the length of the static component
    *       of this string. The goal here is to allocate the backing array of the string
    *       builder to exactly the size it needs to render the entire string without
    *       resizing. There is a test case in TwitterFutureMongoCallbackTest enforcing
    *       the expected string length which will need to be updated if the message here
    *       changes.
    *
    *     - the '+' in the third append call: this takes advantage of the jvm optimizing
    *       away the '+' operation for static strings, allowing us to keep line length in
    *       check here without forcing a fourth .append call
    */
  private def getInterruptedMessage(threadName: String): String = {
    new JavaStringBuilder(threadName.length + 172)
      .append("Mongo client interrupted on thread '")
      .append(threadName)
      .append(
        "' during an async operation (see cause). This probably means Future " +
          "execution is leaking onto threads it shouldn't (eg. a timer thread)."
      )
      .toString
  }

  override protected def processThrowable(throwable: Throwable): Unit = throwable match {
    case mie: MongoInterruptedException => {
      val message = getInterruptedMessage(Thread.currentThread.getName)
      promise.setException(new RogueException(message, mie))
    }

    case other => promise.setException(other)
  }
}

class TwitterFutureMongoCallbackFactory extends MongoCallbackFactory[Future] {

  override def newCallback[T]: TwitterFutureMongoCallback[T] = {
    new TwitterFutureMongoCallback[T]
  }

  override def transformResult[T, U](result: Future[T], f: Try[T] => Future[U]): Future[U] = {
    result.transform(_ match {
      case Return(value) => f(Success(value))
      case Throw(throwable) => f(Failure(throwable))
    })
  }

  override def wrapResult[T](value: => T): Future[T] = Future(value)

  override def wrapException[T](e: Throwable): Future[T] = Future.exception(e)
}
