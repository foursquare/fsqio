// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.common.scala

/**
 * Wrapper for lazy vals used within methods. lazy vals in methods synchronize
 * on the object instance, not in the scope of the method. Thus multiple threads
 * can block on evaluating the same lazy val in the method.
 *
 * <b>ALWAYS USE THIS IF YOU WANT TO USE A lazy val IN A METHOD</b>, e.g.
 *
 * <tt>
 * def myLazilyEvaluatedFunc(..) = {..}
 * val l = LazyLocal(myLazilyEvaluatedFunc)
 * ...
 * l.value
 * </tt>
 */
class LazyLocal[T](f: => T) {
  lazy val value: T = f
}

object LazyLocal {
  def apply[T](f: => T) = {
    new LazyLocal(f)
  }
}
