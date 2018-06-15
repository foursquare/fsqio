// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.common.scala

import com.twitter.scalding.typed.{KeyedListLike, TypedPipe}
import scala.collection.generic.{CanBuildFrom, MapFactory}
import scala.collection.immutable.Map

/** HACK(aaron): A simplified version of io.fsq.common.scala.Lists.Implicits._ for IntelliJ's presentation compiler.
  * IntelliJ doesn't have support for Type Constructors ([[https://youtrack.jetbrains.com/issue/SCL-7974 issue]])
  * which are used extensively in src/io.fsq.common.scala.Lists.Implicits._
  *
  * If you're using IntelliJ and you would like to use this file then you need to right-click
  * src/io.fsq.common.scala.Lists.Implicits._ and select "Mark as Plain Text".
  */
object Lists {
  trait Implicits {
    implicit def immutable2FSIntellijMap[A, B](m: Map[A, B]): FSIntellijMap[A, B, Map[A, B], Map] =
      new FSIntellijMap[A, B, Map[A, B], Map](m, Map)
    implicit def iterable2FSIntellij[T](xs: Iterable[T]): FSIntellijIterable[T] = new FSIntellijIterable[T](xs)
    implicit def seq2FSIntellijSeq[T](xs: Seq[T]): FSIntellijSeq[T] = new FSIntellijSeq[T](xs)
    implicit def mutable2FSIntellijMap[A, B](
      m: scala.collection.mutable.Map[A, B]
    ): FSIntellijMap[A, B, scala.collection.mutable.Map[A, B], scala.collection.mutable.Map] =
      new FSIntellijMap[A, B, scala.collection.mutable.Map[A, B], scala.collection.mutable.Map](
        m,
        scala.collection.mutable.Map
      )
    implicit def option2FSIntellijOption[T](xs: Option[T]): FSIntellijOption[T] = new FSIntellijOption[T](xs)
    implicit def set2FSIntellijSet[T](xs: Set[T]): FSIntellijSet[T] = new FSIntellijSet[T](xs)

    // NOTE(aaron): I'm temporarily adding Scalding implicits here until I find a better home for them.
    implicit def simpleKeyedListToTypedPipe[K, V](xs: KeyedListLike[K, V, _]): TypedPipe[(K, V)] = ???
  }
  object Implicits extends Implicits

  def product[T](lists: List[T]*): List[List[T]] = ???
}

class FSIntellijIterable[T](xs: Iterable[T]) {
  def chunkFlatMap[V](size: Int)(f: Seq[T] => TraversableOnce[V]): Seq[V] = ???
  def chunkMap[V](size: Int)(f: Seq[T] => V): Seq[V] = ???
  def collectFirstOpt[U](f: T => Option[U]): Option[U] = ???
  def distinctBy[U](f: T => U): Seq[T] = ???
  def distinctCounts: Map[T, Int] = ???
  def exactlyOne: Option[T] = ???
  def flatGroupBy[S](f: T => Option[S]): Map[S, Seq[T]] = ???
  def flatMapFind[U](f: T => Option[U]): Option[U] = ???
  def flatToMapBy[K, V](f: T => Option[(K, V)]): Map[K, V] = ???
  def flatToSetBy[U](f: T => TraversableOnce[U]): Set[U] = ???
  def flatToListBy[U](f: T => TraversableOnce[U]): List[U] = ???
  def flatToVectorBy[U](f: T => TraversableOnce[U]): Vector[U] = ???
  def groupByKeyValue[K, V](f: T => (K, V)): Map[K, Seq[V]] = ???
  def groupWhile(f: (T, T) => Boolean): Seq[Seq[T]] = ???
  def has(e: T): Boolean = ???
  def hasOverlap(ys: Iterable[T]): Boolean = ???
  def histogramBy[U](f: T => U): Vector[(U, Int)] = ???
  def indexWhereOption(pred: T => Boolean): Option[Int] = ???
  def maxByOption[U](f: T => U)(implicit ord: Ordering[U]): Option[T] = ???
  def maxOption(implicit ord: Ordering[T]): Option[T] = ???
  def median(implicit ord: Ordering[T]): T = ???
  def medianOpt(implicit ord: Ordering[T]): Option[T] = ???
  def minByOption[U](f: T => U)(implicit ord: Ordering[U]): Option[T] = ???
  def minOption(implicit ord: Ordering[T]): Option[T] = ???
  def sample(n: Int): Seq[T] = ???
  def sumBy[B](f: T => B)(implicit num: Numeric[B]): B = ???
  def shuffled(implicit bf: CanBuildFrom[TraversableOnce[T], T, TraversableOnce[T]]): Seq[T] = ???
  def sortByDesc[B](f: T => B)(implicit ord: Ordering[B]): Seq[T] = ???
  def sortedDesc[B >: T](implicit ord: Ordering[B]): Seq[T] = ???
  def topN(size: Int)(implicit ord: Ordering[T]): Seq[T] = ???
  def toListBy[U](f: T => U): List[U] = ???
  def toMapAccumValues[K, V](implicit ev: T <:< (K, V)): Map[K, Seq[V]] = ???
  def toMapBy[K, V](f: T => (K, V)): Map[K, V] = ???
  def toMapByKey[K](f: T => K): Map[K, T] = ???
  def flatToMapByKey[K](f: T => Option[K]): Map[K, T] = ???
  def countDistinctBy[U](f: T => U): Int = ???
  def toSetBy[U](f: T => U): Set[U] = ???
  def toVectorBy[U](f: T => U): Vector[U] = ???
  def ---(ys: Traversable[T]): Seq[T] = ???
}

class FSIntellijSeq[T](xs: Seq[T]) {
  def tailOption: Option[Seq[T]]
  def yankToIndex(index: Int, pred: T => Boolean): Seq[T] = ???
}

class FSIntellijSet[T](xs: Set[T]) {
  def ---(ys: Traversable[T]): Set[T] = ???
}

class FSIntellijOption[T](val opt: Option[T]) extends AnyVal {
  def flatCollect[B](pf: PartialFunction[T, Option[B]]): Option[B] = ???
  def flatToMapBy[K, V](f: T => Option[(K, V)]): Map[K, V] = ???
  def flatToMapByKey[K](f: T => Option[K]): Map[K, T] = ???
  def has(e: T): Boolean = ???
  def isEmptyOr(pred: T => Boolean): Boolean = ???
  def toMapBy[K, V](f: T => (K, V)): Map[K, V] = ???
  def toMapByKey[K](f: T => K): Map[K, T] = ???
  def toVectorBy[U](f: T => U): Vector[U] = ???
  def flatToVectorBy[U](f: T => TraversableOnce[U]): Vector[U] = ???
  def unzipped[T1, T2](implicit asPair: (T) => (T1, T2)): (Option[T1], Option[T2]) = ???
}

class FSIntellijMap[A, B, This <: scala.collection.Map[A, B] with scala.collection.MapLike[A, B, This], CC[X, Y] <: scala.collection.Map[
  X,
  Y
] with scala.collection.MapLike[X, Y, CC[X, Y]]](
  m: This,
  factory: MapFactory[CC]
)(
  implicit ev1: CC[A, B] =:= This,
  ev2: This =:= CC[A, B]
) {
  def invert[DD[X] <: Traversable[X], B1](
    implicit traversable: B => DD[B1],
    cbf: CanBuildFrom[DD[B1], A, DD[A]]
  ): CC[B1, DD[A]] = ???
  def flattenValues[B1](implicit option: B => Option[B1]): CC[A, B1] = ???
  def mappedValues[C](f: B => C): CC[A, C] = ???
  def flatMapValues[C](f: B => Option[C]): CC[A, C] = ???
}
