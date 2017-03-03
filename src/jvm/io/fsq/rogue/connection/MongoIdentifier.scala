// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.connection


object MongoIdentifier {
  def apply(name: String): MongoIdentifier = new MongoIdentifier(name)
}

/** A simple String wrapper identifying a mongo connection. */
class MongoIdentifier(val name: String) extends AnyVal {
  override def toString: String = name
}
