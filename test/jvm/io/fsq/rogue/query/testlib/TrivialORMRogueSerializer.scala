// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.query.testlib

import io.fsq.rogue.MongoHelpers.MongoSelect
import io.fsq.rogue.query.RogueSerializer
import org.bson.Document

class TrivialORMRogueSerializer extends RogueSerializer[TrivialORMMetaRecord[_], TrivialORMRecord, Document] {

  override def readFromDocument[M <: TrivialORMMetaRecord[_], R](
    meta: M,
    selectOpt: Option[MongoSelect[M, R]]
  )(
    document: Document
  ): R = selectOpt match {
    case Some(MongoSelect(fields, transformer)) => {
      val selectedFields = fields.map(field => {
        field.valueOrDefault(Option(document.get(field.field.name)))
      })
      transformer(selectedFields)
    }

    case None => meta.fromDocument(document).asInstanceOf[R]
  }

  override def writeToDocument[R <: TrivialORMRecord](record: R): Document = {
    record.meta.toDocument(record)
  }
}
