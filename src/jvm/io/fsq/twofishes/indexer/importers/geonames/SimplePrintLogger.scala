// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.
package io.fsq.twofishes.indexer.importers.geonames
  
trait SimplePrintLogger {
  object logger {
    def error(s: String) { println("**ERROR** " + s)}
    def info(s: String) { println(s)}
  }
}
