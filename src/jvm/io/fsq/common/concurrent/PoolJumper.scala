// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.common.concurrent

import com.twitter.util.{Future, FuturePool}

/**
 * PoolJumper is a utility used in a for comprehension
 * of futures that can control the execution context
 * of the yield block
 *
 * PoolJumper implements the interface for
 * Futures.runYieldInPool, used in the following example
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
trait PoolJumper {
  def map[T](f: Function1[Unit, T]): Future[T]
}

final class DefaultPoolJumper(pool: FuturePool) extends PoolJumper {
  def map[T](f: Function1[Unit, T]): Future[T] = pool(f())
}
