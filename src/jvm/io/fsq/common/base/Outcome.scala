// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.common.base

/**
 * Represents the outcome of an action where an action results in the disjoint union of two states: success
 * or failure. The only instances of [[io.fsq.base.Outcome]] are [[io.fsq.base.Success]] and
 * [[io.fsq.base.Failure]]. By default, [[io.fsq.base.Outcome]] operates as a monad over the
 * successful state.
 */
sealed trait Outcome[+S, +F] {
  def isSuccess: Boolean
  def isFailure: Boolean

  def either: Either[F, S]
  def toOption: Option[S]

  def failure: FailureProjection[S, F] = new FailureProjection(this)

  def fold[T](onSuccess: S => T, onFailure: F => T): T

  def exists(f: S => Boolean): Boolean
  def forall(f: S => Boolean): Boolean

  def map[T](f: S => T): Outcome[T, F]
  def flatMap[T, FF >: F](f: S => Outcome[T, FF]): Outcome[T, FF]
  def foreach[T](f: S => T)

  def flatten[T, FF >: F](implicit asOutcome: (S) => Outcome[T, FF]): Outcome[T, FF]
  def rescue[SS >: S, FF](f: F => Outcome[SS, FF]): Outcome[SS, FF]
  def filter[FF >: F](f: S => Boolean, failure: FF): Outcome[S, FF]
}

object Outcome {
  def apply[S, F](opt: Option[S], f: => F): Outcome[S, F] = opt match {
    case Some(s) => Success(s)
    case None => Failure(f)
  }

  // Returns the failure value if cond is true, else Success(Unit). Useful in comprehensions.
  def failWhen[F](cond: Boolean, f: => F): Outcome[Unit, F] = {
    if (cond) Failure(f) else Success(())
  }

  def allSuccess[S, F](os: Seq[Outcome[S, F]]): Outcome[Seq[S], F] = {
    val failures = os.flatMap(_ match {
      case Failure(f) => Some(f)
      case Success(_) => None
    })
    failures match {
      case f +: _ => Failure(f)
      case Nil => Success(os.flatMap(_.toOption))
    }
  }

  def apply[T](f: => T): Outcome[T, String] = {
    try { Success(f) } catch {
      case ex: Exception => Failure(ex.getMessage)
    }
  }
}

/**
 * A successful outcome, instead of a [[io.fsq.base.Failure]].
 */
final case class Success[+S, +F](v: S) extends Outcome[S, F] {
  override def isSuccess = true
  override def isFailure = false

  override def either = Right(v)
  override def toOption = Some(v)

  override def fold[T](onSuccess: S => T, onFailure: F => T): T = onSuccess(v)

  override def exists(f: S => Boolean) = f(v)
  override def forall(f: S => Boolean) = f(v)

  override def map[T](f: S => T): Outcome[T, F] = Success(f(v))
  override def flatMap[T, FF >: F](f: S => Outcome[T, FF]) = f(v)
  override def foreach[T](f: S => T): Unit = { f(v) }

  override def flatten[T, FF >: F](implicit asOutcome: (S) => Outcome[T, FF]): Outcome[T, FF] = asOutcome(v)
  override def rescue[SS >: S, FF](f: F => Outcome[SS, FF]): Outcome[SS, FF] = Success(v)
  override def filter[FF >: F](f: S => Boolean, failure: FF): Outcome[S, FF] = if (f(v)) Success(v) else Failure(failure)
}

/**
 * A failed outcome, instead of a [[io.fsq.base.Success]].
 */
final case class Failure[+S, +F](v: F) extends Outcome[S, F] {
  override def isSuccess = false
  override def isFailure = true

  override def either = Left(v)
  override def toOption = None

  override def fold[T](onSuccess: S => T, onFailure: F => T): T = onFailure(v)

  override def exists(f: S => Boolean) = false
  override def forall(f: S => Boolean) = true

  override def map[T](f: S => T): Outcome[T, F] = Failure(v)
  override def flatMap[T, FF >: F](f: S => Outcome[T, FF]) = Failure(v)
  override def foreach[T](f: S => T): Unit = { /* pass */  }

  override def flatten[T, FF >: F](implicit asOutcome: (S) => Outcome[T, FF]): Outcome[T, FF] = Failure(v)
  override def rescue[SS >: S, FF](f: F => Outcome[SS, FF]): Outcome[SS, FF] = f(v)
  override def filter[FF >: F](f: S => Boolean, failure: FF): Outcome[S, FF] = Failure(v)
}

/**
 * Projects an [[io.fsq.base.Outcome]] as a monad.
 */
final case class FailureProjection[+S, +F](underlying: Outcome[S, F]) {
  def toOption: Option[F] = underlying match {
    case Success(_) => None
    case Failure(v) => Some(v)
  }

  def exists(f: F => Boolean): Boolean = underlying match {
    case Success(_) => false
    case Failure(v) => f(v)
  }

  def forall(f: F => Boolean): Boolean = underlying match {
    case Success(_) => true
    case Failure(v) => f(v)
  }

  def map[T](f: F => T): FailureProjection[S, T] = underlying match {
    case Success(v) => FailureProjection(Success(v))
    case Failure(v) => FailureProjection(Failure(f(v)))
  }

  def flatMap[T](f: F => Option[T]): Option[T] = underlying match {
    case Success(_) => None
    case Failure(v) => f(v)
  }

  def foreach[T](f: F => T): Unit = {
    underlying match {
      case Success(_) =>
      case Failure(v) => f(v)
    }
  }

  def filter(f: F => Boolean): Option[F] = underlying match {
    case Success(_) => None
    case Failure(v) => if (f(v)) Some(v) else None
  }
}
