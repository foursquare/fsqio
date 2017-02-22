// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.query


/** TODO(jacob): Move off of this model to the new Codec system. */
trait RogueSerializer[Record, Document] {
  def readFromDocument(document: Document): Record
  def writeToDocument(record: Record): Document
}
