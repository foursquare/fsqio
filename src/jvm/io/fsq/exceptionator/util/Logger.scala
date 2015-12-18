// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.util

import com.twitter.logging.{ConsoleHandler, Level, LoggerFactory}

object Logger {
  lazy val configured = {
    com.twitter.logging.Logger.get("").clearHandlers
    LoggerFactory(level = Some(Level.INFO), handlers = List(ConsoleHandler())).apply()
  }
}

trait Logger {
  val logger = {
    val _ = Logger.configured
    com.twitter.logging.Logger.get(getClass)
  }
}
