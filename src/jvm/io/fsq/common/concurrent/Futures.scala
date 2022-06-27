// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.common.concurrent

import com.twitter.finagle.{GlobalRequestTimeoutException, IndividualRequestTimeoutException}
import com.twitter.util.{
  Await,
  Duration,
  Future,
  FuturePool,
  Promise,
  Return,
  Stopwatch,
  TimeoutException => TUTimeoutException,
  Timer,
  Try
}
import io.fsq.common.scala.Lists.Implicits._
import io.fsq.macros.StackElement
import java.util.concurrent.{ArrayBlockingQueue, TimeoutException}
import scala.collection.JavaConverters.asJavaCollectionConverter

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
    if (f.isDefined) f else f.within(timer, timeout, new FilledTimeoutException(timeout.toString, caller))
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
    *   a) The future completes successfully, and its value is returned.
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
    future
      .within(timer, timeout)
      .rescue({
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
    future
      .within(timer, timeout)
      .rescue({
        case _: TimeoutException | _: IndividualRequestTimeoutException | _: GlobalRequestTimeoutException => {
          onTimeoutFunc.foreach(_())
          Future.value(defaultValue)
        }
      })
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

  def awaitSuccessfulWithinTimeout[A](
    futures: Seq[Future[A]],
    timeout: Duration,
    onFailure: (Throwable) => Unit = _ => ()
  )(implicit timer: Timer, caller: StackElement): Seq[A] = {
    val triesF = {
      Future.collectToTry(
        futures.map(
          _.within(timer, timeout, new FilledTimeoutException(timeout.toString, caller))
            .onFailure(onFailure)
        )
      )
    }
    val results = Await.result(triesF)
    results.collect({ case Return(x) => x })
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
    if (params.isEmpty) {
      Future.value(Seq.empty[U])

    } else if (params.size <= limit) {
      Future.collect(params.toVector.map(f(_)))

    } else {
      val (initial, others) = params.zipWithIndex.splitAt(limit)
      val othersQueue = new ArrayBlockingQueue(others.size, false, others.asJavaCollection)
      val results = Vector.fill(params.size)(new Promise[U])
      @volatile var exceptional = false

      def compute(param: T, index: Int): Unit = results(index).become({
        f(param)
          .onFailure(_ => exceptional = true)
          .onSuccess(
            _ =>
              if (!exceptional) {
                Option(othersQueue.poll()).foreach({
                  case (nextParam, nextIndex) => compute(nextParam, nextIndex)
                })
              }
          )
      })

      // Kick off our initial batch of computation. Upon completion, each Future will
      // pull another param from the queue and start it on its merry way without waiting
      // for the entire batch to finish.
      initial.foreach({ case (param, index) => compute(param, index) })
      Future.collect(results)
    }
  }

  /**
    * If we generate a large list of futures at once, we risk overloading the future pool. This
    * allows us to generate futures in small groups and chain them together.
    */
  def groupedCollect[T, U, V](params: Map[T, U], limit: Int)(f: U => Future[V]): Future[Map[T, V]] = {
    if (params.isEmpty) {
      Future.value(Map.empty[T, V])
    } else if (params.size <= limit) {
      Future.collect(params.mappedValues(f(_)))
    } else {
      val (initial, others) = params.toVector.zipWithIndex.splitAt(limit)
      val othersQueue = new ArrayBlockingQueue(others.size, false, others.asJavaCollection)
      val results = params.mappedValues(_ => new Promise[V])
      @volatile var exceptional = false

      def compute(param: (T, U)): Unit = results(param._1).become({
        f(param._2)
          .onFailure(_ => exceptional = true)
          .onSuccess(
            _ =>
              if (!exceptional) {
                Option(othersQueue.poll()).foreach({
                  case (nextParam, nextIndex) => compute(nextParam)
                })
              }
          )
      })

      // Kick off our initial batch of computation. Upon completion, each Future will
      // pull another param from the queue and start it on its merry way without waiting
      // for the entire batch to finish.
      initial.foreach({ case (param, index) => compute(param) })
      Future.collect(results)
    }
  }

  /**
    * groupedCollectWithBatch performs the same operation as groupedCollect but does a .grouped on the input params first
    * Effectively, you get up to limit Futures running over group items from params at any given time.
    */
  def groupedCollectWithBatch[T, U](params: Iterable[T], batchSize: Int, maxFutures: Int)(
    f: Iterable[T] => Future[U]
  ): Future[Seq[U]] = {
    Futures.groupedCollect(params.grouped(batchSize).toList, maxFutures)(f)
  }

  /**
    * Like groupedCollect, but executes every future. If the executed futures have side effects,
    * then this is what you want to do. If there is an exception, the resulting futures will still be executed.
    * The returned Future will contain the first exception thrown.
    */
  def groupedExecute[T](params: Iterable[T], limit: Int)(f: T => Future[Unit]): Future[Unit] = {
    Futures
      .groupedTry(params, limit)(f)
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

  /** runs the yield block of a for comprehension in an explicit execution context
    *
    * xF, yF might be finagle futures, runYieldInPool will
    * run doBlockingWork in a safe execution context.
    *
    * for {
    *   x <- xF
    *   y <- yF
    *   _ <- Futures.runYieldInPool(pool)
    * } yield doBlockingWork
    */
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

  /**
    * Returns a new Future whose return value will be a tuple of the value of
    * `futureFunc`, and the number of milliseconds that elapsed between
    * calling `time` and the completion of `futureFunc`. Note that if you create
    * `futureFunc` before wrapping it in`time`, then the time returned might be
    * less than the actual amount of time that `futureFunc` ran.
    *
    */
  def time[T](futureFunc: => Future[T]): Future[(T, Int)] = {
    val elapsed = Stopwatch.start()
    futureFunc.map(x => {
      (x, elapsed().inMilliseconds.toInt)
    })
  }
}
