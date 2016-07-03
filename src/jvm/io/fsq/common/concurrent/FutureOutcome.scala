// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.common.concurrent

import com.twitter.util.Future
import io.fsq.common.base.{Failure, Outcome, Success}
import io.fsq.common.scala.Identity._
import io.fsq.common.scala.Lists.Implicits._

/**
 *  Inspired by [[io.fsq.common.concurrent.FutureOption]], this is a monadic wrapper
 *  around Future[Outcome[S, F]] so you can easily combine Future[S], Option[S], Outcome[S, F], etc.
 *  into a for yield.
 *
 * Example:
 *
 *  val result: Future[Outcome[Venue, String]] = (for {
 *    userid <- FutureOutcome.lift(msg.useridOption, "no-userid")
 *    user <- FutureOutcome.lift(services.futureDb.fetchOne(Q(User).where(_.userid eqs userid)), "no-user")
 *    venue <- FutureOutcome(services.foo.bar(user)) // where bar returns Future[Outcome[Venue, String]]
 *    _ <- FutureOutcome.failWhen(venue.isHomeOrOffice, "home-or-office-venue")
 *  } yield venue).resolve
 */
final class FutureOutcome[+S, +F](val resolve: Future[Outcome[S, F]]) extends AnyVal {
  def map[T](f: S => T): FutureOutcome[T, F] = new FutureOutcome(resolve.map(_.map(f)))

  def flatMap[T, FF >: F](f: S => FutureOutcome[T, FF]): FutureOutcome[T, FF] =
    new FutureOutcome(resolve.flatMap {
      case Success(s) => f(s).resolve
      case Failure(fa) => Future(Failure(fa))
    })

  def foreach[T](f: S => Unit): Unit = new FutureOutcome(resolve.foreach(_.foreach(f)))
  def filter[FF >: F](f: S => Boolean, failure: FF): FutureOutcome[S, FF] = {
    new FutureOutcome(resolve.map(_.filter(f, failure)))
  }
  def withFilter[FF >: F](f: S => Boolean, failure: FF): FutureOutcome[S, FF] = {
    new FutureOutcome(resolve.map(_.filter(f, failure)))
  }

  def flatten[T, FF >: F](implicit asFutureOutcome: (S) => FutureOutcome[T, FF]): FutureOutcome[T, FF] = {
    this.flatMap(asFutureOutcome)
  }
}

object FutureOutcome {
  def apply[S, F](f: Future[Outcome[S, F]]): FutureOutcome[S, F] = new FutureOutcome(f)
  def apply[S, F](o: Outcome[Future[S], F]): FutureOutcome[S, F] = new FutureOutcome(o match {
    case Success(fs) => fs.map(s => Success(s))
    case Failure(fa) => Future.value(Failure(fa))
  })

  def lift[S, F](f: Future[S]): FutureOutcome[S, F] = new FutureOutcome(f.map(Success(_)))
  def lift[S, F](o: Outcome[S, F]): FutureOutcome[S, F] = new FutureOutcome(Future.value(o))
  def lift[S, F](o: Option[S], fa: => F): FutureOutcome[S, F] = new FutureOutcome(Future.value(Outcome(o, fa)))
  def lift[S, F](fo: Future[Option[S]], fa: => F): FutureOutcome[S, F] = new FutureOutcome(fo.map(o => Outcome(o, fa)))

  def value[S, F](a: S): FutureOutcome[S, F] = new FutureOutcome(Future.value(Success(a)))

  def exception(e: Exception): FutureOutcome[Nothing, Nothing] = new FutureOutcome(Future.exception(e))

  def failure[F](fa: => F): FutureOutcome[Nothing, F] = new FutureOutcome(Future.value(Failure(fa)))
  def success[S](s: => S): FutureOutcome[S, Nothing] = new FutureOutcome(Future.value(Success(s)))

  // Returns the failure value if cond is true, else Future[Success(Unit)]. Useful in for comprehensions.
  def failWhen[F](cond: Boolean, f: => F): FutureOutcome[Unit, F] = {
    if (cond) FutureOutcome.failure(f) else FutureOutcome.success(Unit)
  }
}
