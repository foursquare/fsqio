// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.common.concurrent

import com.twitter.finagle.{GlobalRequestTimeoutException, IndividualRequestTimeoutException}
import com.twitter.util.{Await, Duration, Future, FuturePool, TimeoutException => TUTimeoutException, Timer, Try}
import io.fsq.macros.StackElement
import java.util.concurrent.TimeoutException

/**
 * Handy helpers for dealing with Futures.
 */
object Futures {
  val SentinelElement = new StackTraceElement("io.fsq", "ASYNC", "Futures.scala", -1)

  class FilledTimeoutException(
    msg: String,
    caller: StackTraceElement
  ) extends TUTimeoutException(caller.getClassName + ":" + msg) {
    override def fillInStackTrace: Throwable = {
      super.fillInStackTrace()
      val currentStackTrace = getStackTrace
      val newStackTrace = new Array[StackTraceElement](currentStackTrace.length + 2)
      newStackTrace(0) = caller
      newStackTrace(1) = SentinelElement
      System.arraycopy(currentStackTrace, 0, newStackTrace, 2, currentStackTrace.length)
      setStackTrace(newStackTrace)
      this
    }
  }

  def within[T](f: Future[T], timeout: Duration)(implicit timer: Timer, caller: StackElement): Future[T] = {
    within(f, timer, timeout)(caller)
  }

  def within[T](f: Future[T], timer: Timer, timeout: Duration)(implicit caller: StackElement): Future[T] = {
    f.within(timer, timeout, new FilledTimeoutException(timeout.toString, caller))
  }

  /**
   * A helper function for for-comprehensions. Useful for handling the common
   * case where you do not want to proceed if a condition is false.
   *
   * Ex:
   *
   * val result: Future[Option[Int]] = for {
   *   likeCountOpt <- Futures.where(userSupportsLikes, fetchLikeCount())
   * } yield likeCountOpt
   */
  def where[T](cond: Boolean, f: => Future[T]): Future[Option[T]] = {
    if (cond) {
      f.map(r => Some(r))
    } else Future.value(None)
  }

  /**
   * Blocks on the future, until the future completes, or the timeout is reached. There are three possibilities:
   *   a) The future completes succesfully, and it's value is returned.
   *   b) The future times out, and default is returned.
   *   c) The future throws an exception, and then this call will throw.
   *
   * Please consider using non-blocking future handling, such as .map, .flatMap, or .rescue instead of this function.
   */

  def awaitWithDefault[T, U <: T](f: Future[T], timeout: Duration, timeoutDefault: => U): T = {
    try {
      Await.result(f, timeout)
    } catch {
      case _: TimeoutException | _: GlobalRequestTimeoutException | _: IndividualRequestTimeoutException =>
        timeoutDefault
    }
  }

  /**
   * If the future doesn't return within the duration, then call
   * onTimeoutFunc
   *
   * Calls onTimeoutFunc but still throws
   */
  def withTimeoutThrow[T](
    future: Future[T],
    timer: Timer,
    timeout: Duration,
    errorMessage: String,
    onTimeoutFunc: Option[() => Unit] = None
  ): Future[T] = {
    future.within(timer, timeout).rescue({
      case e: TimeoutException =>
        onTimeoutFunc.foreach(_())
        Future.exception(new TimeoutException("Critical Timeout(%s): %s".format(errorMessage, e.getMessage)))
      case e: IndividualRequestTimeoutException =>
        onTimeoutFunc.foreach(_())
        Future.exception(new TimeoutException("Critical IRTimeout(%s): %s".format(errorMessage, e.getMessage)))
      case e: GlobalRequestTimeoutException =>
        onTimeoutFunc.foreach(_())
        Future.exception(new TimeoutException("Critical IRTimeout(%s): %s".format(errorMessage, e.getMessage)))
    })
  }

  /**
   * If the future doesn't return within the duration, then call
   * onFailureOption and returns the default value instead
   */
  def withTimeout[T](
    future: Future[T],
    timer: Timer,
    defaultValue: T,
    timeout: Duration,
    onTimeoutFunc: Option[() => Unit] = None
  ): Future[T] = {
    future.within(timer, timeout).rescue({
      case _: TimeoutException |
           _: IndividualRequestTimeoutException |
           _: GlobalRequestTimeoutException => {
        onTimeoutFunc.foreach(_())
        Future.value(defaultValue)
      }})
  }

  /**
   * Unpack a future whose result is a tuple into a tuple of futures
   */
  def unzip[A, B](future: Future[(A, B)]): (Future[A], Future[B]) = {
    (future.map(_._1), future.map(_._2))
  }

  /**
   * Unpack a future whose result is a tuple into a tuple of futures
   */
  def unzip[A, B, C](future: Future[(A, B, C)]): (Future[A], Future[B], Future[C]) = {
    (future.map(_._1), future.map(_._2), future.map(_._3))
  }

  /**
   * Unpack a future whose result is a tuple into a tuple of futures
   */
  def unzip[A, B, C, D](future: Future[(A, B, C, D)]): (Future[A], Future[B], Future[C], Future[D]) = {
    (future.map(_._1), future.map(_._2), future.map(_._3), future.map(_._4))
  }

  /**
   * If we generate a large list of futures at once, we risk overloading the future pool. This
   * allows us to generate futures in small groups and chain them together.
   * Maintains the order of the Iterable.
   */
  def groupedCollect[T, U](params: Iterable[T], limit: Int)(f: T => Future[U]): Future[Seq[U]] = {
    // NOTE: We reverse the sequence twice, so that we are prepending to the Seq
    // instead of appending, which will be faster when the Seq is a linked list.
    // We also foldLeft instead of foldRight, to handle the case when params is an Iterator, and
    // so we are not required to consume the whole Iterator before proceeding.
    params.grouped(limit)
      .foldLeft(Future.value(Seq.empty[U]))((previousValuesF, nextParams) => for {
        previousValues <- previousValuesF
        nextValues <- Future.collect(nextParams.toVector.map(p => f(p)))
      } yield nextValues.reverse ++ previousValues)
      .map(_.reverse)
  }

  /**
   * Like groupedCollect, but executes every future. If the executed futures have side effects,
   * then this is what you want to do. If there is an exception, the resulting futures will still be executed.
   * The returned Future will contain the first exception thrown.
   */
  def groupedExecute[T](params: Iterable[T], limit: Int)(f: T => Future[Unit]): Future[Unit] = {
    Futures.groupedTry(params, limit)(f)
      .map(results => results.find(_.isThrow).foreach(_.apply()))
  }

  /**
   * Like groupedExecute, but exposes the results of each executed future as a Try. If your computations have
   * side-effects, but you also want to know which sub-requests executed successfully, you can inspect each
   * returned Try.
   */
  def groupedTry[T, U](params: Iterable[T], limit: Int)(f: T => Future[U]): Future[Seq[Try[U]]] = {
    def tryF(value: T): Future[Try[U]] = {
      // NOTE: .transform(Future.value) is equivalent to .liftToTry, which is not available
      // in our current version of twitter-util
      f(value).transform(Future.value)
    }

    Futures.groupedCollect(params, limit)(tryF)
  }

  /** runs the yield block of a for comprehension in an explicit exection context
   *
   * xF, yF might be finagle futures, runYieldInPool will
   * run doBlockingWork in a safe execution context.
   *
   * for {
   *   x <- xF
   *   y <- yF
   *   _ <- Futures.runYieldInPool(pool)
   * } yield doBlockingWork*/
  def runYieldInPool(pool: FuturePool): PoolJumper = new DefaultPoolJumper(pool)

  /**
   * Run the func, but turn any Throwables thrown into a Future.exception so
   * that later code can assume it is always dealing with a future
   */
  def safeReturnFuture[T](func: => Future[T]): Future[T] = {
    try {
      func
    } catch {
      case t: Throwable => Future.exception(t)
    }
  }
}
