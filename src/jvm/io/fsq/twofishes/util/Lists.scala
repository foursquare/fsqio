package io.fsq.twofishes.util

import scala.collection.TraversableLike
import scala.collection.generic.{CanBuildFrom, GenericTraversableTemplate, MapFactory}
import scala.collection.mutable.Builder

object Lists {
  trait Implicits {
    implicit def seq2FSTraversable[CC[X] <: Traversable[X], T, Repr <: TraversableLike[T, Repr]](xs: TraversableLike[T, Repr] with GenericTraversableTemplate[T, CC]): FSTraversable[CC, T, Repr] = new FSTraversable[CC, T, Repr](xs)

    implicit def opt2FSOpt[T](o: Option[T]) = new FSOption(o)
    implicit def fsopt2Opt[T](fso: FSOption[T]) = fso.opt

    implicit def immutable2FSMap[A, B](m: Map[A, B]): FSMap[A, B, Map[A, B], Map] = new FSMap[A, B, Map[A, B], Map](m, Map)
    implicit def mutable2FSMap[A, B](m: scala.collection.mutable.Map[A, B]): FSMap[A, B, scala.collection.mutable.Map[A, B], scala.collection.mutable.Map] = new FSMap[A, B, scala.collection.mutable.Map[A, B], scala.collection.mutable.Map](m, scala.collection.mutable.Map)
  }

  object Implicits extends Implicits
}

class FSTraversableOnce[T](xs: TraversableOnce[T]) {
  def toVector: Vector[T] =
    if (xs.isInstanceOf[Vector[_]])
      xs.asInstanceOf[Vector[T]]
    else
      (Vector.newBuilder[T] ++= xs).result
}


class FSTraversable[CC[X] <: Traversable[X], T, Repr <: TraversableLike[T, Repr]](xs: TraversableLike[T, Repr] with GenericTraversableTemplate[T, CC]) extends FSTraversableOnce(xs) {
  def has(e: T): Boolean = xs match {
    case xSet: Set[_] => xSet.asInstanceOf[Set[T]].contains(e)
    case xMap: Map[_,_] => {
      val p = e.asInstanceOf[Pair[Any,Any]]
      xMap.asInstanceOf[Map[Any,Any]].get(p._1) == Some(p._2)
    }
    case _ => xs.exists(_ == e)
  }

  /**
   * Return the smallest element from the Seq according to the supplied sort function.
   */
  def minByOption[U](f: T => U)(implicit ord: Ordering[U]): Option[T] = {
    var first = true
    var min: Option[T] = None
    var minValue: Option[U] = None

    for (x <- xs) {
      if (first) {
        min = Some(x)
        minValue = Some(f(x))
        first = false
      }

      val value = f(x)

      if (ord.lt(value, minValue.get)) {
        min = Some(x)
        minValue = Some(value)
      }
    }

    min
  }
}

class FSOption[T](val opt: Option[T]) {
  def has(e: T): Boolean = opt.exists(_ == e)
  def isEmptyOr(pred: T => Boolean): Boolean = opt.forall(pred)
  def unzipped[T1, T2](implicit asPair: (T) => (T1, T2)): (Option[T1], Option[T2]) = opt match {
    case Some(x) => {
      val pair = asPair(x)
      (Some(pair._1), Some(pair._2))
    }
    case None => (None, None)
  }
}

class FSMap[
  A,
  B,
  This <: scala.collection.Map[A, B] with scala.collection.MapLike[A, B, This],
  CC[X, Y] <: scala.collection.Map[X, Y] with scala.collection.MapLike[X, Y, CC[X, Y]]
](
  m: This,
  factory: MapFactory[CC]
)(
  implicit ev1: CC[A, B] =:= This, ev2: This =:= CC[A, B]
) {
  def invert[DD[X] <: Traversable[X], B1](
    implicit traversable: B => DD[B1],
    cbf: CanBuildFrom[DD[B1], A, DD[A]]
  ): CC[B1, DD[A]] = {
    val intermediate = scala.collection.mutable.Map.empty[B1, Builder[A, DD[A]]]
    for {
      (a, bs) <- m
      b <- bs
    } {
      val builder = intermediate.getOrElseUpdate(b, cbf())
      builder += a
    }

    val rv = factory.newBuilder[B1, DD[A]]
    for ((k, v) <- intermediate)
      rv += ((k, v.result()))

    rv.result()
  }

  def flattenValues[B1](implicit option: B => Option[B1]): CC[A, B1] = {
    val rv = factory.newBuilder[A, B1]
    for {
      (k, vOpt) <- m
      v <- option(vOpt)
    } rv += ((k, v))
    rv.result()
  }

  def mappedValues[C](f: B => C): CC[A, C] = {
    val rv = factory.newBuilder[A, C]
    for {
      (a, b) <- m
    } rv += ((a, f(b)))
    rv.result()
  }

  def flatMapValues[C](f: B => Option[C]): CC[A, C] = {
    val rv = factory.newBuilder[A, C]
    for {
      (a, b) <- m
      c <- f(b)
    } rv += ((a, c))
    rv.result()
  }
}
