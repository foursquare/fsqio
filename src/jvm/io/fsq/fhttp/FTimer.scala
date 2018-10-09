// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.fhttp

import com.twitter.util.{Duration, Timer}
import java.util.concurrent.{Executors, ThreadFactory}

object FTimer {
  private val threadFactory = new ThreadFactory {
    val default = Executors.defaultThreadFactory
    override def newThread(r: Runnable): Thread = {
      val thread = default.newThread(r)
      thread.setDaemon(true)
      thread
    }
  }

  val finagleTimer: Timer = com.twitter.finagle.util.HashedWheelTimer(threadFactory, Duration.fromMilliseconds(10))
}
