// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.common.logging

import com.twitter.logging.{ConsoleHandler, Level, Logger => TwitterLogger, LoggerFactory}

object Logger {
  lazy val configured = {
    val defaultLogger = com.twitter.logging.Logger.get("")
    val currentHandlers = defaultLogger.getHandlers().map(_.getClass.getName)
    /* Hack: Presto's logging configuration (which redirects stdout/err to its log) causes a stackoverflow  because
     * ConsoleHandler explicitly logs to stderr. The class is not public, and so cannot be referenced via classOf here.
     */
    if (!currentHandlers.contains("io.airlift.log.OutputStreamHandler")) {
      defaultLogger.clearHandlers
      LoggerFactory(level = Some(Level.INFO), handlers = List(ConsoleHandler())).apply()
    }
  }
}

trait Logger {
  val logger = {
    val _ = Logger.configured
    TwitterLogger.get(getClass)
  }
}
