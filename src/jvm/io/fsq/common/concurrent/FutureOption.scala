// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.common.concurrent

import com.twitter.util.Future

/**
 * A monadic wrapper around the common case of Future[Option[A]].
 * Allows you to use for-comprehensions around Future[Option[A]]
 * without needing to special case each None case.
 *
 * Example:
 *
 *  val result: Future[Option[Venue]] = (for {
 *    user <- FutureOption(services.futureDb.fetchOne(Q(User).where(_.id eqs 32)))
 *    tip <- FutureOption(services.futureDb.fetchOne(Q(Tip).where(_.userid eqs user.id)))
 *    venue <- FutureOption(services.futureDb.fetchOne(Q(Venue).where(_.id eqs tip.venueid)))
 *    if !venue.isHome
 *  } yield venue).resolve
 *
 *
 * Example 2:
 *   val sixteenFOpt = FutureOption(Future.value(Some(16)))
 *   for { s <- sixteenFOpt } { println(s) }
 *   This will output "16".
 *
 * FutureOption is a value class. http://docs.scala-lang.org/overviews/core/value-classes.html
 */
final class FutureOption[+A](val resolve: Future[Option[A]]) extends AnyVal {
  def map[T](f: A => T): FutureOption[T] =
    new FutureOption(resolve.map(_.map(f)))

  def flatMap[T](f: A => FutureOption[T]): FutureOption[T] =
    new FutureOption(resolve.flatMap {
      case Some(a) => f(a).resolve
      case None => Future.None
    })

  def foreach[T](f: A => Unit): Unit = new FutureOption(resolve.foreach(_.foreach(f)))
  def filter(f: A => Boolean): FutureOption[A] = new FutureOption(resolve.map(_.filter(f)))
  def withFilter(f: A => Boolean): FutureOption[A] = new FutureOption(resolve.map(_.filter(f)))

  def flatten[T](implicit asFutureOption: (A) => FutureOption[T]): FutureOption[T] =
    this.flatMap(asFutureOption)

  // If the underlying Option is None, evaluate and fall back to the provided FutureOption.
  // This does NOT rescue any exceptions that are thrown. If the first FutureOption throws, the second
  // will not be evaluated, and the return value will be a thrown exception
  def orElse[B >: A](f: => FutureOption[B]): FutureOption[B] = FutureOption(
    resolve.flatMap(_ match {
      case Some(v) => Future.value(Some(v))
      case scala.None => f.resolve
    }))

  def getOrElse[B >: A](f: => Future[B]): Future[B] = {
    resolve.flatMap(_ match {
      case Some(v) => Future.value(v)
      case scala.None => f
    })}

  // Joins this wrapping Future with the other wrapping Future.  The new
  // FutureOption will have an underlying Option of None if either of the
  // joined FutureOptions have an underlying None. It carries an Option
  // of a Tuple, not a tuple of options (just do a resolve.join(other.resolve)
  // for that behavior).
  def join[T](other: FutureOption[T]): FutureOption[(A, T)] = {
    val joinedOptF = resolve.join(other.resolve).map({
      case (Some(self), Some(other)) => Some((self, other))
      case _ => None
    })
    new FutureOption(joinedOptF)
  }
}

object FutureOption {
  def apply[A](f: Future[Option[A]]): FutureOption[A] = new FutureOption(f)
  def apply[A](o: Option[Future[A]]): FutureOption[A] = new FutureOption(o match {
    case Some(fa) => fa.map(a => Some(a))
    case scala.None => Future.value(scala.None)
  })

  def lift[A](f: Future[A]) = new FutureOption(f.map(Option(_)))
  def lift[A](o: Option[A]) = new FutureOption(Future.value(o))

  def value[A](a: A): FutureOption[A] = new FutureOption(Future.value(Option(a)))

  def exception(e: Exception): FutureOption[Nothing] = new FutureOption(Future.exception(e))

  val None: FutureOption[Nothing] = new FutureOption(Future.None)
}
