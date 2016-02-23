// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.common.scala

import scala.annotation.tailrec
import scala.collection.{IterableLike, SeqLike, SetLike, TraversableLike}
import scala.collection.generic.{CanBuildFrom, GenericCompanion, GenericSetTemplate, GenericTraversableTemplate,
    MapFactory}
import scala.collection.immutable.{Map, VectorBuilder}
import scala.collection.mutable.{ArrayBuffer, ArraySeq, Builder, HashMap, PriorityQueue}
import scala.util.Random

object Arrays {
  def swap[T](arr: ArraySeq[T], a: Int, b: Int): Unit = {
    if (a != b) {
      val temp = arr(a)
      arr.update(a, arr(b))
      arr.update(b, temp)
    }
  }

  case class PartitionResult(leftLength: Int, rightLength: Int)

  def partitionInPlace[T](arr: ArraySeq[T], pivot: T)(implicit ord: Ordering[T]): PartitionResult =
    partitionInPlace(arr, pivot, 0, arr.size)
  def partitionInPlace[T](
    arr: ArraySeq[T],
    pivot: T,
    beginIndex: Int,
    endIndex: Int
  )(
    implicit ord: Ordering[T]
  ): PartitionResult = {
    val headIndex = beginIndex
    val tailIndex = endIndex - 1
    var leftLength = 0
    var rightLength = 0

    while (headIndex + leftLength <= tailIndex - rightLength) {
      val i = headIndex + leftLength
      val j = tailIndex - rightLength
      if (ord.gt(arr(i), pivot)) {
        swap(arr, i, j)
        rightLength += 1
      } else leftLength += 1
    }

    PartitionResult(leftLength, rightLength)
  }
}

object Lists {
  /**
   *  Remove all elements from left that are in right
   */
  def removeAll[T, Repr, R <: Traversable[T]](left: TraversableLike[T, Repr], right: R): Repr = {
    //this is tricky, but doing toSet in the closure will cause to be re-evaluated on every iteration
    val rightSet = right.toSet
    left.filterNot(rightSet.contains _)
  }

  /**
   * Like removeAll, but uses a key function to determine equality. Useful for using IDs to determine
   * equality
   */
  def removeAllBy[T, K, Repr, R <: Traversable[T]](left: TraversableLike[T, Repr], right: R)(f: T => K) = {
    val rightSet = right.map(f).toSet
    left.filterNot(l => rightSet.contains(f(l)))
  }

  /**
   * Build a map with a custom function for aggregating collisions
   */
  def aggregate[T, K, A](list: Iterable[T])(getKey: T => K)(agg: (Option[A], T) => A): Map[K, A] = {
    val m = scala.collection.mutable.Map[K, A]()
    list.foreach(x => {
      val key = getKey(x)
      m.update(key, agg(m.get(key), x))
    })
    m.toMap
  }

  /**
   * Cartesian product of an arbitrary number of lists
   * Iterating starts from the first list to the last list
   * e.g. List(1,2), List(3,4) returns List(1,3),
   *                                   List(2,3),
   *                                   List(1,4),
   *                                   List(2,4)
   */
  def product[T](lists: List[T]*): List[List[T]] = lists.toList match {
    case Nil => List(Nil): List[List[T]]
    case h +: t => for {
      p <- product(t: _*)
      i <- h
    } yield i +: p
  }

  /**
   * Cartesian product of an arbitrary number of lists
   * Iterating starts from the last list to the first list
   * e.g. List(1,2), List(3,4) returns List(1,3),
   *                                   List(1,4),
   *                                   List(2,3),
   *                                   List(2,4)
   */
  def productReverse[T](lists: List[T]*): List[List[T]] = lists.toList match {
    case Nil => List(Nil): List[List[T]]
    case h +: t => {
      val p = productReverse(t: _*)
      for {
        i <- h
        r <- p
      } yield i +: r
    }
  }

  /**
   * Returns the powerset of a List
   *
   * If you don't care about ordering use Set(1,2,3).subsets.
   */
  def powerset[T](xs: List[T]): List[List[T]] = xs match {
    case y +: ys => {
      val ps = powerset(ys)
      ps.map(y +: _) ++ ps
    }
    case Nil => List(Nil)
  }

  /**
   * Zips with a binary operation
   */
  def zipWith[A,B,C](as: Seq[A], bs: Seq[B])(f: (A, B) => C): Seq[C] = {
    as.zip(bs).map({ case (a, b) => f(a, b) })
  }

  /**
   * Finds the nth smallest value in an unsorted list in O(n) time and O(n) space
   * E.g., nth(List(3,2,4,1), 0) => 1 and nth(List(3,2,4,1), 2) => 3
   */
  def nth[T](seq: Seq[T], target: Int)(implicit ord: Ordering[T]): Option[T] = {
    @tailrec
    def nthHelper[T](
      beginIndex: Int,
      endIndex: Int,
      target: Int,
      arr: ArraySeq[T]
    )(
      implicit ord: Ordering[T]
    ): Option[T] = {
      if (beginIndex == endIndex) {
        None
      } else {
        val pivotIndex = beginIndex + Rand.rand.nextInt(endIndex - beginIndex)
        val pivot = arr(pivotIndex)
        Arrays.swap(arr, beginIndex, pivotIndex)
        val headIndex = beginIndex + 1

        val partitionResult = Arrays.partitionInPlace(arr, pivot, headIndex, endIndex)
        val (leftLength, rightLength) = (partitionResult.leftLength, partitionResult.rightLength)

        if (target < leftLength) {
          nthHelper(headIndex, endIndex - rightLength, target, arr)
        } else if (target == leftLength) {
          Some(pivot)
        } else {
          nthHelper(endIndex - rightLength, endIndex, target - leftLength - 1, arr)
        }
      }
    }

    nthHelper(0, seq.size, target, ArraySeq() ++ seq)
  }

  /**
   * Functions so simple their type signature determines their implementation
   */
  def sequence1[A, B](t: (Option[A], B)): Option[(A, B)] = t._1.map(a => (a, t._2))
  def sequence2[A, B](t: (A, Option[B])): Option[(A, B)] = t._2.map(b => (t._1, b))
  def map12[A, B, C, D](t: (A, B))(f1: A => C, f2: B => D): (C, D) = (f1(t._1), f2(t._2))
  def map1[A, B, C](t: (A, B))(f: A => C): (C, B) = map12(t)(f, identity[B])
  def map2[A, B, C](t: (A, B))(f: B => C): (A, C) = map12(t)(identity[A], f)

  trait Implicits {
    implicit def companion2FSCompanion[CC[X] <: Traversable[X]](companion: GenericCompanion[CC]): FSCompanion[CC] = new FSCompanion[CC](companion)

    implicit def set2FSSet[CC[X] <: scala.collection.Set[X], T, Repr <: SetLike[T, Repr] with scala.collection.Set[T]
            with GenericSetTemplate[T, CC]](xs: SetLike[T, Repr] with scala.collection.Set[T] with GenericSetTemplate[T, CC]): FSSet[CC, T, Repr] = new FSSet[CC, T, Repr](xs)

    implicit def seq2FSTraversable[CC[X] <: Traversable[X], T, Repr <: TraversableLike[T, Repr]](xs: TraversableLike[T, Repr] with GenericTraversableTemplate[T, CC]): FSTraversable[CC, T, Repr] = new FSTraversable[CC, T, Repr](xs)
    implicit def seq2FSIterable[CC[X] <: Iterable[X], T, Repr <: IterableLike[T, Repr] with GenericTraversableTemplate[T, CC]](xs: IterableLike[T, Repr] with GenericTraversableTemplate[T, CC]): FSIterable[CC, T, Repr] = new FSIterable[CC, T, Repr](xs)
    implicit def seq2FSSeq[CC[X] <: Seq[X], T, Repr <: SeqLike[T, Repr] with GenericTraversableTemplate[T, CC]](xs: SeqLike[T, Repr] with GenericTraversableTemplate[T, CC]): FSSeq[CC, T, Repr] = new FSSeq[CC, T, Repr](xs)
    implicit def seq2FSTraversableOnce[T, CC[X] <: TraversableOnce[X]](xs: CC[T])(implicit bf: CanBuildFrom[CC[T], T, CC[T]]): FSTraversableOnce[T, CC] = new FSTraversableOnce[T, CC](xs)
    implicit def array2FSSeq[T <: AnyRef](xs: Array[T]): FSSeq[ArraySeq, T, ArraySeq[T]] = new FSSeq[ArraySeq, T, ArraySeq[T]](new ArraySeq[T](xs.size) { override val array: Array[AnyRef] = xs.asInstanceOf[Array[AnyRef]] })

    implicit def opt2FSOpt[T](o: Option[T]): FSOption[T] = new FSOption(o)
    implicit def fsopt2Opt[T](fso: FSOption[T]): Option[T] = fso.opt

    implicit def immutable2FSMap[A, B](m: Map[A, B]): FSMap[A, B, Map[A, B], Map] = new FSMap[A, B, Map[A, B], Map](m, Map)
    implicit def mutable2FSMap[A, B](m: scala.collection.mutable.Map[A, B]): FSMap[A, B, scala.collection.mutable.Map[A, B], scala.collection.mutable.Map] = new FSMap[A, B, scala.collection.mutable.Map[A, B], scala.collection.mutable.Map](m, scala.collection.mutable.Map)
  }

  object Implicits extends Implicits
}

class FSCompanion[CC[X] <: Traversable[X]](val companion: GenericCompanion[CC]) extends AnyVal {
  /**
   * Unfolds an item into a CC
   */
  def unfold[T, R](init: T)(f: T => Option[(T, R)]): CC[R] = {
    val builder = companion.newBuilder[R]
    var start = f(init)
    while (start.isDefined) {
      val (next, r) = start.get
      builder += r
      start = f(next)
    }
    builder.result()
  }
}

class FSTraversableOnce[T, CC[X] <: TraversableOnce[X]](val xs: CC[T]) extends AnyVal {
  def shuffled(implicit bf: CanBuildFrom[CC[T], T, CC[T]]): CC[T] = Random.shuffle(xs)
  def shuffledDeterministically(seed: Long)(implicit bf: CanBuildFrom[CC[T], T, CC[T]]): CC[T] =
    (new Random(seed)).shuffle(xs)

  /**
   *  Returns n randomly selected elements from the given list
   */
  def sample(n: Int): Iterable[T] = {
    val iter = xs.toIterator
    val reservoir = new ArrayBuffer[T](n)

    var i: Int = 0
    while (i < n && iter.nonEmpty) {
      reservoir += iter.next
      i += 1
    }

    if (iter.isEmpty) {
      reservoir.take(i)
    } else {
      while (iter.nonEmpty) {
        val r = Rand.rand.nextInt(i + 1)
        i += 1
        if (r < n) {
          reservoir(r) = iter.next
        } else {
          val _ = iter.next
        }
      }
      reservoir
    }
  }
}

class FSTraversable[CC[X] <: Traversable[X], T, Repr <: TraversableLike[T, Repr]](
  val xs: TraversableLike[T, Repr] with GenericTraversableTemplate[T, CC]
) extends AnyVal {
  def newBuilder[A] = xs.companion.newBuilder[A]

  /**
   * Create a new collection which separates the elements of the original collection by sep
   */
  def mkJoin[S >: T](sep: S): CC[S] = {
    var first = true
    val builder = newBuilder[S]
    for (x <- xs) {
      if (!first) builder += sep
      builder += x
      first = false
    }
    builder.result()
  }

  def has(e: T): Boolean = xs match {
    case xSet: Set[_] => xSet.asInstanceOf[Set[T]].contains(e)
    case xMap: Map[_,_] => {
      val p = e.asInstanceOf[(Any,Any)]
      xMap.asInstanceOf[Map[Any,Any]].get(p._1) == Some(p._2)
    }
    case _ => xs.exists(_ == e)
  }

  def flatGroupBy[S](f: T => Option[S]): Map[S, Repr] = {
    xs.groupBy(f).flatMap {
      case (Some(s), ts) => Some(s, ts)
      case _ => None
    }
  }

  def mapAccum[A, B](a: A)(f: (A, T) => (A, B)): (A, CC[B]) = {
    val builder = newBuilder[B]
    val accum = xs.foldLeft(a) { case (accum, x) =>
      val (nextAccum, b) = f(accum, x)
      builder += b
      nextAccum
    }

    (accum, builder.result())
  }

  def flatMapAccum[A, B](a: A)(f: (A, T) => (A, TraversableOnce[B])): (A, CC[B]) = {
    val builder = newBuilder[B]
    val accum = xs.foldLeft(a) { case (accum, x) =>
      val (nextAccum, bs) = f(accum, x)
      builder ++= bs
      nextAccum
    }

    (accum, builder.result())
  }

  def distinctBy[U](f: T => U): CC[T] = {
    val builder = newBuilder[T]
    val seen = scala.collection.mutable.Set.empty[U]

    for (x <- xs) {
      val u = f(x)
      if (!seen(u)) {
        builder += x
        seen += u
      }
    }

    builder.result()
  }

  def flatDistinctBy[U](f: T => Option[U]): CC[T] = {
    val builder = newBuilder[T]
    val seen = scala.collection.mutable.Set.empty[U]

    for (x <- xs) {
      val uOpt = f(x)
      uOpt.foreach(u =>
        if (!seen(u)) {
          builder += x
          seen += u
        }
      )
    }

    builder.result()
  }

  def countDistinctBy[U](f: T => U): Int = {
    val seen = scala.collection.mutable.Set.empty[U]
    for (x <- xs) {
      val u = f(x)
      if (!seen(u)) {
        seen += u
      }
    }
    seen.size
  }

  /**
   * Return a Map whose keys are the items in this Iterable, and whose
   * values are the number of times that item occurred in the list
   */
  def distinctCounts: Map[T, Int] = {
    val m = scala.collection.mutable.Map.empty[T, Int]

    for (x <- xs) {
      m.get(x) match {
        case None => m(x) = 1
        case Some(i) => m(x) = i + 1
      }
    }
    m.toMap
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

  def minOption(implicit ord: Ordering[T]): Option[T] = minByOption(identity[T])
  def maxOption(implicit ord: Ordering[T]): Option[T] = maxByOption(identity[T])
  def maxByOption[U](f: T => U)(implicit ord: Ordering[U]): Option[T] = minByOption(f)(ord.reverse)

  def collectFirstOpt[U](f: T => Option[U]): Option[U] = {
    for (x <- xs) {
      val opt = f(x)
      if (opt.isDefined)
        return opt
    }
    None
  }

  /**
   * Return a list of lists divided by the provided predicate.
   */
  def groupWhile(f: (T, T) => Boolean): CC[CC[T]] = {
    val yss = newBuilder[CC[T]]
    var ys = newBuilder[T]
    var prev: Option[T] = None

    for (x <- xs) {
      for (p <- prev) {
        if (!f(p, x)) {
          yss += ys.result
          ys = newBuilder[T]
        }
      }

      ys += x
      prev = Some(x)
    }

    val last = ys.result()
    if (last.nonEmpty) {
      yss += last
    }
    yss.result()
  }

  /**
   * Return a sublist of items where at most N items for any given
   * predicate value are included. Preserves order within a group,
   * but not the order of predicates (yet!).
   */
  def crowd[K](limit: Int, p: T => K): CC[T] = {
    val builder = newBuilder[T]
    for ((_, ts) <- xs.groupBy(p)) {
      builder ++= ts.take(limit)
    }
    builder.result()
  }

  def ---(ys: Traversable[T]): Repr = {
    Lists.removeAll(xs, ys)
  }

  /**
   * Return a histogram (sequence of of (item, count) elements) by a
   * given function.
   *
   * Usage:
   *   Seq("Do", "Re", "Mi", "Fa", "So", "La", "Ti", "Do").histogramBy(_.last)
   *   // returns: Vector((o,3), (a,2), (i,2), (e,1))
   */
  def histogramBy[U](f: T => U): Vector[(U, Int)] = {
    val builder = new VectorBuilder[(U, Int)]
    for ((u, ts) <- xs.groupBy(f)) {
      builder += ((u, ts.size))
    }
    builder.result().sortBy(- _._2)
  }
}

class FSSet[
  CC[X] <: scala.collection.Set[X],
  T,
  Repr <: SetLike[T, Repr] with scala.collection.Set[T] with GenericSetTemplate[T, CC]
](
 val xs: SetLike[T, Repr] with GenericSetTemplate[T, CC]
) extends AnyVal {
  def newBuilder[T] = xs.companion.newBuilder[T]

  def has(e: T): Boolean = xs.contains(e)
}

class FSIterable[CC[X] <: Iterable[X], T, Repr <: IterableLike[T, Repr] with GenericTraversableTemplate[T, CC]](
  val xs: IterableLike[T, Repr] with GenericTraversableTemplate[T, CC]
) extends AnyVal {
  def newBuilder[A] = xs.companion.newBuilder[A]

  def chunkMap[V](size: Int)(f: Repr => V): CC[V] = {
    val builder = newBuilder[V]
    xs.grouped(size).foreach(ts => builder += f(ts))
    builder.result()
  }

  /**
   * Convenience wrapper for Iterable.sliding(2) that gives you #exactlywhatyouwant: tuples instead of Lists
   */
  def slidingPairs: CC[(T, T)] = {
    val builder = newBuilder[(T, T)]
    for {
      it <- xs.sliding(2)  // Iterable[_] <- Iterator[Iterable[_]]
      if it.size == 2
    } {
      builder += ((it.head, it.tail.head))
    }
    builder.result()
  }

  def slidingOptPairs: CC[(T, Option[T])] = {
    val builder = newBuilder[(T, Option[T])]

    @tailrec
    def slidingOptPairsRec(arr: IterableLike[T, Repr] with GenericTraversableTemplate[T, CC]): Unit = {
      val rest = arr.drop(1)
      (arr.headOption, rest.headOption) match {
        case (None, _) =>
        case (Some(first), secondOpt) => {
          builder += ((first, secondOpt))
          slidingOptPairsRec(rest)
        }
      }
    }

    slidingOptPairsRec(xs)
    builder.result()
  }

  /**
   * Break a big list into smaller chunks and map each chunk
   */
  def chunkFlatMap[V](size: Int)(f: Repr => TraversableOnce[V]): CC[V] = {
    val builder = newBuilder[V]
    xs.grouped(size).foreach(ts => builder ++= f(ts))
    builder.result()
  }

  /**
   * Filter out elements that match the predicate, applying the supplied
   * function to each filtered out element, and returning the remaining elements.
   */
  def filterOutWith(pred: T => Boolean, f: T => Unit): CC[T] = {
    val builder = newBuilder[T]
    xs.foreach(x => if (pred(x)) f(x) else builder += x)
    builder.result()
  }

  /** Applies `f` to each item in the collection and returns a Set
   */
  def toSetBy[U](f: T => U): Set[U] = {
    val builder = Set.newBuilder[U]

    xs.foreach(x => {
      builder += f(x)
    })

    builder.result()
  }

  /** Applies `f` to each item in the collection and returns a Map, discarding
   *  duplicates.
   */
  def toMapBy[K, V](f: T => (K, V)): Map[K, V] = {
    val builder = Map.newBuilder[K, V]

    xs.foreach(x => {
      builder += f(x)
    })

    builder.result()
  }

  /** Applies `f` to each item in the collection and returns a Set
   */
  def flatToSetBy[U](f: T => TraversableOnce[U]): Set[U] = {
    val builder = Set.newBuilder[U]

    xs.foreach(x => {
      f(x).foreach(y => builder += y)
    })

    builder.result()
  }

  /** Applies `f` to each item in the collection and returns a Map, discarding
   *  duplicates.
   */
  def flatToMapBy[K, V](f: T => Option[(K, V)]): Map[K, V] = {
    val builder = Map.newBuilder[K, V]

    xs.foreach(x => {
      f(x).foreach(v => builder += v)
    })

    builder.result()
  }

  /** Creates a map from a sequence, mapping each element in the sequence by a given key.
   *  If keys are duplicated, values will be discarded.
   */
  def toMapByKey[K](f: T => K): Map[K, T] = {
    toMapBy(elem => (f(elem), elem))
  }

  /** Creates a map from a sequence, mapping each element in the sequence by a given Option[key].
    *  If keys are duplicated or None, values will be discarded.
    */
  def flatToMapByKey[K](f: T => Option[K]): Map[K, T] = {
    flatToMapBy(elem => f(elem).map((_, elem)))
  }

  /** Groups the given sequence by a function that transforms the sequence into keys and values.
    * For example. `Seq(1 -> "a", 2 -> "a", "1" -> "b").groupByKeyValue(x => x)` will return
    * `Map(1 -> List(a, b), 2 -> List(a))`
    */
  def groupByKeyValue[K, V](f: T => (K, V)): Map[K, Seq[V]] = {
    val m = new HashMap[K, Builder[V, Seq[V]]]()

    for (x <- xs) {
      val (k, v) = f(x)
      val bldr = m.getOrElseUpdate(k, Seq.newBuilder)
      bldr += v
    }

    val retval = Map.newBuilder[K, Seq[V]]
    for ((k, v) <- m) {
      retval += (k -> v.result)
    }

    retval.result()
  }

  /** Like `toMap`, but accumulates the values into a Seq, rather than
    * discarding the values of duplicated keys.
    */
  def toMapAccumValues[K, V](implicit ev: T <:< (K, V)): Map[K, Seq[V]] = {
    groupByKeyValue(x => x)
  }

  /** Returns the top N elements in the Iterable.
    * They'll come back in no particular order.
    */
  def topN(size: Int)(implicit ord: Ordering[T]): CC[T] = {
    val pq = new PriorityQueue[T]()(ord.reverse)
    xs.foreach(x => {
      if (pq.size < size || ord.gt(x, pq.head)) {
        pq.enqueue(x)
        if (pq.size > size) {
          pq.dequeue()
        }
      }
    })
    val builder = newBuilder[T]
    builder ++= pq.result()
    builder.result()
  }

  /** Applies `f` to each item in the collection and returns a List
    */
  def toListBy[U](f: T => U): List[U] = {
    val builder = List.newBuilder[U]

    xs.foreach(x => {
      builder += f(x)
    })

    builder.result()
  }

  /** Applies `f` to each item in the collection and returns a List
    */
  def flatToListBy[U](f: T => TraversableOnce[U]): List[U] = {
    val builder = List.newBuilder[U]

    xs.foreach(x => {
      f(x).foreach(y => builder += y)
    })

    builder.result()
  }


  /** Applies `f` to each item in the collection and returns a Vector
    */
  def toVectorBy[U](f: T => U): Vector[U] = {
    val builder = Vector.newBuilder[U]

    xs.foreach(x => {
      builder += f(x)
    })

    builder.result()
  }

  /** Applies `f` to each item in the collection and returns a Vector
    */
  def flatToVectorBy[U](f: T => TraversableOnce[U]): Vector[U] = {
    val builder = Vector.newBuilder[U]

    xs.foreach(x => {
      f(x).foreach(y => builder += y)
    })

    builder.result()
  }

  /** Applies `f` to each item in the collection until the first Some[U] is found and return it, or None otherwise
    */
  def flatMapFind[U](f: T => Option[U]): Option[U] = {
    val iter = xs.toIterator
    var transformed: Option[U] = None
    while (iter.nonEmpty && transformed.isEmpty) {
      transformed = f(iter.next)
    }
    transformed
  }

  def exactlyOne: Option[T] = {
    val iter = xs.iterator
    if (iter.hasNext) {
      val x = iter.next
      if (iter.hasNext) {
        None
      } else {
        Some(x)
      }
    } else None
  }

  def hasOverlap(ys: Iterable[T]): Boolean = {
    val ysSet = ys.toSet
    xs.exists(x => ysSet.contains(x))
  }
}

class FSSeq[CC[X] <: Seq[X], T, Repr <: SeqLike[T, Repr] with GenericTraversableTemplate[T, CC]](
  val xs: SeqLike[T, Repr] with GenericTraversableTemplate[T, CC]
) extends AnyVal {
  def newBuilder[A] = xs.companion.newBuilder[A]

  def has(e: T): Boolean = xs.contains(e)

  def yankToIndex(index: Int, pred: T => Boolean) = {
    val (head, tail) = xs.splitAt(index)
    val (middle, end) = tail.partition(pred)

    val builder = newBuilder[T]
    builder ++= head
    builder ++= middle
    builder ++= end
    builder.result()
  }

  /**
   *  Like findIndexOf but returns None instead of -1 if the element is not in the list
   */
  def indexWhereOption(pred: T => Boolean): Option[Int] = {
    xs.indexWhere(pred) match {
      case -1 => None
      case i => Some(i)
    }
  }

  /**
   *  Like indexOf but returns None instead of -1 if the element is not in the list
   */
  def indexOfOption(target: T): Option[Int] = {
    xs.indexOf(target) match {
      case -1 => None
      case i => Some(i)
    }
  }

  /**
   *  Returns n randomly selected elements from the given list
   */
  def sample(n: Int): CC[T] = {
    val builder = newBuilder[T]
    val size = xs.size
    var i = 0
    var left = n

    for (x <- xs) {
      if (Rand.rand.nextInt(size - i) < left) {
        builder += x
        left -= 1
      }
      i += 1
    }

    builder.result()
  }

  def sample(p: Double): CC[T] = sample((xs.length * p).toInt)

  /**
   * Finds the nth smallest value in an unsorted list in O(n) time and O(1) space
   * ie List(3,2,4,1).nth(0) => 1 and List(3,2,4,1).nth(2) => 3
   **/
  final def nth(target: Int)(implicit ord: Ordering[T]): Option[T] = Lists.nth(xs.toVector, target)

  /**
   * Finds the median value in an unsorted list in O(n) expected time.
   * Will throw if the list is empty.
   */
  def median(implicit ord: Ordering[T]): T = medianOpt.get


  /**
   * Finds the median value in an unsorted list in O(n) expected time.
   * Does not throw if the list is empty as you probably don't want that.
   */
  def medianOpt(implicit ord: Ordering[T]): Option[T] = nth(xs.length / 2)


  /**
   * Finds the item at the target cumulative weight in an unsorted weighted list in O(n) time.
   */
  final def pth[S](target: Double)(implicit ev: T => (S, Double), ord: Ordering[S]): Option[S] =
    if (xs.isEmpty) {
      None
    } else {
      val pivotIndex = Rand.rand.nextInt(xs.size)
      val pivot = xs(pivotIndex)
      val (left, right) = xs.tail.partition(x => ord.lteq(x._1, pivot._1))
      val leftSum = left.view.map(_._2).sum
      val leftSumPlusPivot = leftSum + pivot._2
      if (target < leftSum) {
        (new FSSeq[CC, T, Repr](left)).pth(target)
      } else if (target < leftSumPlusPivot) {
        Some(pivot._1)
      } else {
        (new FSSeq[CC, T, Repr](right)).pth(target - leftSumPlusPivot)
      }
    }

  def sortByDesc[B](f: T => B)(implicit ord: Ordering[B]): Repr = xs.sortBy[B](f)(ord.reverse)

  def sortedDesc[B >: T](implicit ord: Ordering[B]): Repr = xs.sorted[B](ord.reverse)

  /** Inserts a new element into the sequence after the first element which matches the predicate.
   * If the predicate isn't matched then the newElement will not be inserted.
   *
   * @param predicateFn function for determining which element to insert after
   * @param newElement new element to be added
   * @return a new sequence with the element inserted
   */
  def insertAfter(predicateFn: (T => Boolean), newElement: T): CC[T] = {
    val builder = newBuilder[T]
    var hasInsertedNewElement = false

    for (element <- xs) {
      builder += element
      if (!hasInsertedNewElement && predicateFn(element)) {
        builder += newElement
        hasInsertedNewElement = true
      }
    }

    builder.result()
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
  def hasKey(x: A): Boolean = m.contains(x)

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

class FSOption[T](val opt: Option[T]) extends AnyVal {
  def has(e: T): Boolean = opt.exists(_ == e)

  def isEmptyOr(pred: T => Boolean): Boolean = opt.forall(pred)

  def unzipped[T1, T2](implicit asPair: (T) => (T1, T2)): (Option[T1], Option[T2]) = opt match {
    case Some(x) => {
      val pair = asPair(x)
      (Some(pair._1), Some(pair._2))
    }
    case None => (None, None)
  }

  def toMapBy[K, V](f: T => (K, V)): Map[K, V] = opt match {
    case Some(x) => Map(f(x))
    case None => Map.empty
  }

  def flatToMapBy[K, V](f: T => Option[(K, V)]): Map[K, V] = opt.flatMap(f) match {
    case Some((k, v)) => Map(k -> v)
    case _ => Map.empty
  }

  def toMapByKey[K](f: T => K): Map[K, T] = {
    toMapBy(elem => (f(elem), elem))
  }

  def flatToMapByKey[K](f: T => Option[K]): Map[K, T] = {
    flatToMapBy(elem => f(elem).map((_, elem)))
  }

  def flatCollect[B](pf: PartialFunction[T, Option[B]]): Option[B] = {
    opt.collect(pf).flatten
  }

  def toVectorBy[U](f: T => U): Vector[U] = {
    opt match {
      case Some(x) => Vector(f(x))
      case None => Vector.empty
    }
  }

  def flatToVectorBy[U](f: T => TraversableOnce[U]): Vector[U] = {
    opt.map(x => f(x).toVector).getOrElse(Vector.empty)
  }
}

object Rand {
  lazy val rand = new scala.util.Random
}
