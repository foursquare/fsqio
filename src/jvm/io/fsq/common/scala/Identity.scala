// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.common.scala

import scala.math.Numeric

final class Identity[A](val _value: A) extends AnyVal {
  def =?(other: A): Boolean = _value == other
  def !=?(other: A): Boolean = _value != other
  def ==>[B](other: => B)(implicit evA: A <:< Boolean, evB: B <:< Boolean): Boolean = !evA(_value) || evB(other)
  def !=>[B](other: => B)(implicit evA: A <:< Boolean, evB: B <:< Boolean): Boolean = evA(_value) && !evB(other)
  def optionally[B](other: => B)(implicit ev: A <:< Boolean): Option[B] = if (ev(_value)) Some(other) else None
  def flatOptionally[B](other: => Option[B])(implicit ev: A <:< Boolean): Option[B] = if (ev(_value)) other else None
  def ifOption[B](pred: A => Boolean)(f: A => B): Option[B] = if (pred(_value)) Some(f(_value)) else None

  def applyIf[B >: A](pred: Boolean, f: A => B): B = if (pred) f(_value) else _value
  def applyIfFn[B >: A](predFn: B => Boolean, f: A => B): B = if (predFn(_value)) f(_value) else _value
  def applyOpt[B](opt: Option[B])(f: (A, B) => A): A = opt.map(b => f(_value, b)).getOrElse(_value)

  def withMinOf(other: A)(implicit n: Numeric[A]): A = n.max(other, _value)
  def withMaxOf(other: A)(implicit n: Numeric[A]): A = n.min(other, _value)

  def between(lo: A, hi: A)(implicit ord: Ordering[A]): Boolean = ord.lteq(lo, _value) && ord.lt(_value, hi)
}

object Identity {
  implicit def wrapIdentity[A](anything: A): Identity[A] = new Identity(anything)
  implicit def unwrapIdentity[A](id: Identity[A]): A = id._value
}
