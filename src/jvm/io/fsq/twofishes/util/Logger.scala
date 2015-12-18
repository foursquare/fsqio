// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.
package io.fsq.twofishes.util

trait TwofishesLogger {
  def ifDebug(formatSpecifier: String, va: Any*)
}
