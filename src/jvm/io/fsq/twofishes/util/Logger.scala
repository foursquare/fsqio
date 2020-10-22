// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.
package io.fsq.twofishes.util

trait TwofishesLogger {
  def ifDebug(formatSpecifier: String, va: Any*): Unit
  def logDuration[T](ostrichKey: String, what: String)(f: => T): T
  def ifLevelDebug(level: Int, formatSpecifier: String, va: Any*): Unit
  def getLines: List[String]
}
