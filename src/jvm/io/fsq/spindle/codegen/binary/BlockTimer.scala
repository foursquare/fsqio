// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.
// NOTE(awinter): ideally this belongs in common but we're keeping it here to
//   play nice with builds.

package io.fsq.spindle.codegen.binary

import scala.collection.mutable

/* Helper class storing the timing information for each block in BlockTimer. */
class BlockDets() {
  var totalTimeNanos: Long = 0
  var calls: Int = 0

  def bump(nanos: Long): Unit = {
    calls += 1
    totalTimeNanos += nanos
  }
}

/* HashMap wrapper that times blocks of code. */
class BlockTimer {
  val blocks: mutable.Map[String, BlockDets] = mutable.Map[String, BlockDets]()
  var currentName: String = null
  var startTime: Long = 0

  /* Call this at the top of a block. Calls [[stop]] internally. */
  def start(name: String): Unit = {
    stop()
    currentName = name
    startTime = System.nanoTime()
  }

  /*
  Call once before [[render]] to close your final block. Safe to call multiple times.
  Used internally by [[start]].
   */
  def stop(): Unit = {
    val now: Long = System.nanoTime()
    if (currentName != null) {
      val dets = blocks.getOrElseUpdate(currentName, new BlockDets)
      dets.bump(now - startTime)
    }
    currentName = null
    startTime = now
  }

  /* Human-readable string for each block. */
  def render(): Iterable[String] =
    for ((k, dets) <- blocks)
      yield s"BlockDets($k ms=${dets.totalTimeNanos / 1000000} calls=${dets.calls})"
}
