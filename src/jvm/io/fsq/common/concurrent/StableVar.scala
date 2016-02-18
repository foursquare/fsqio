// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.common.concurrent

import com.twitter.util.{Closable, Event, Var, Witness}
import io.fsq.common.scala.Lists.Implicits._

/**
 * An [[com.twitter.util.Event]] that only notifies if the observed value actually changes.
 */
class StableEvent[T](sourceEvent: Event[T]) extends Event[T] {
  override def register(w: Witness[T]): Closable = {
    val witness = new Witness[T] {
      @volatile private var priorValueOpt: Option[T] = None

      override def notify(newValue: T): Unit = {
        if (!priorValueOpt.has(newValue)) {
          priorValueOpt = Some(newValue)
          w.notify(newValue)
        } else {
          priorValueOpt = Some(newValue)
        }
      }
    }

    sourceEvent.register(witness)
  }
}

object StableVar {
  /**
   * Create a Var[T] from `source` that only reports changes if the observed value actually changes.
   */
  def apply[T](init: T, source: Var[T]): Var[T] = {
    Var(init, new StableEvent(source.changes))
  }
}
