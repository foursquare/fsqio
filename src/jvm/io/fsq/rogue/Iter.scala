// Copyright 2020 Foursquare Labs, Inc. All Rights Reserved

package io.fsq.rogue

/**
  * Iteratee helper classes, modeled off of a Subscriber interface.
  * An (Event[T] => Command[S]) is like a Subscriber[T] that mutates some state S
  */
object Iter {

  /** @tparam S state type */
  sealed trait Command[S] {
    def state: S
  }
  case class Continue[S](state: S) extends Command[S]
  case class Return[S](state: S) extends Command[S]
  // TODO(iant): we should be able to handle exceptions with
  // case class Throw(e: Exception) extends Command[Nothing] { def state = throw e }

  /** @tparam R the type of element provided by OnNext */
  sealed trait Event[+R]
  case class OnNext[R](r: R) extends Event[R]
  case class OnError(e: Exception) extends Event[Nothing]
  case object OnComplete extends Event[Nothing]
}

object IterUtil {
  def batchCommand[S, R](
    groupSize: Int,
    processGroup: (S, Vector[R]) => Iter.Command[S]
  ): ((S, Vector[R]), Iter.Event[R]) => Iter.Command[(S, Vector[R])] = {
    case ((state, group), Iter.OnNext(item)) if group.size + 1 >= groupSize =>
      processGroup(state, group :+ item) match {
        case Iter.Continue(state1) => Iter.Continue((state1, Vector()))
        case Iter.Return(state1) => Iter.Return((state1, Vector()))
      }
    case ((state, group), Iter.OnNext(item)) =>
      Iter.Continue((state, group :+ item))
    case ((state, group), Iter.OnComplete) =>
      val state1 = processGroup(state, group).state
      Iter.Return((state1, Vector()))
    case (_, Iter.OnError(e)) =>
      throw e
  }

  // No Iter.Return allowed
  def batch[S, R](
    groupSize: Int,
    processGroup: (S, Vector[R]) => S
  ): ((S, Vector[R]), Iter.Event[R]) => Iter.Command[(S, Vector[R])] = {
    batchCommand[S, R](groupSize, (s, rs) => Iter.Continue(processGroup(s, rs)))
  }

}
