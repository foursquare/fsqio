// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.connection


object MongoIdentifier {
  def apply(name: String): MongoIdentifier = new DefaultMongoIdentifier(name)
}

/** A simple String wrapper identifying a mongo connection. */
trait MongoIdentifier {
  def name: String
  override def toString: String = name
}

class DefaultMongoIdentifier(override val name: String) extends MongoIdentifier
