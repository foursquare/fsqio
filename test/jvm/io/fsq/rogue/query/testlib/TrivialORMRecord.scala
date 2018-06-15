// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.query.testlib

import io.fsq.rogue.connection.MongoIdentifier
import org.bson.Document

/** A trivial ORM layer that implements the interfaces rogue needs. The goal is to make
  * sure that rogue-core works without the assistance of rogue-lift. Ideally this would be
  * even smaller; as it is, there is still some code copied from the Lift implementations.
  */
trait TrivialORMRecord {
  type Self >: this.type <: TrivialORMRecord
  def meta: TrivialORMMetaRecord[Self]
}

trait TrivialORMMetaRecord[R] {
  def collectionName: String
  def mongoIdentifier: MongoIdentifier
  def fromDocument(document: Document): R
  def toDocument(record: R): Document
}
