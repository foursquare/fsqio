// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.query

import io.fsq.rogue.MongoHelpers.MongoSelect

/** TODO(jacob): Move off of this model to the new Codec system. */
trait RogueSerializer[MetaRecord, Record, Document] {

  def readFromDocument[M <: MetaRecord, R](
    meta: M,
    selectOpt: Option[MongoSelect[M, R]]
  )(
    document: Document
  ): R

  def writeToDocument[R <: Record](record: R): Document
}
