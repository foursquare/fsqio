// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.connection

import io.fsq.common.scala.Identity._

object MongoIdentifier {
  def apply(name: String): MongoIdentifier = new DefaultMongoIdentifier(name)
}

/** A simple String wrapper identifying a mongo connection. */
trait MongoIdentifier {
  def name: String

  override def equals(other: Any): Boolean = other match {
    case id: MongoIdentifier => id.name =? id.name
    case _ => false
  }

  override def hashCode(): Int = name.hashCode

  override def toString: String = name
}

class DefaultMongoIdentifier(override val name: String) extends MongoIdentifier
