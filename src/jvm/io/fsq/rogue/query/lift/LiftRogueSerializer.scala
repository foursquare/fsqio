// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.query.lift

import com.mongodb.BasicDBObject
import io.fsq.rogue.MongoHelpers.MongoSelect
import io.fsq.rogue.lift.LiftQueryExecutorHelpers
import io.fsq.rogue.query.RogueSerializer
import net.liftweb.mongodb.record.{MongoMetaRecord, MongoRecord}

class LiftRogueSerializer
  extends RogueSerializer[MongoRecord[_] with MongoMetaRecord[_], MongoRecord[_], BasicDBObject] {

  override def readFromDocument[M <: MongoRecord[_] with MongoMetaRecord[_], R](
    meta: M,
    selectOpt: Option[MongoSelect[M, R]]
  )(
    document: BasicDBObject
  ): R = selectOpt match {
    case Some(MongoSelect(fields, transformer)) => {
      val intermediateRecord = meta.createRecord.asInstanceOf[MongoRecord[_]]

      val selectedFields = fields.map(field => {
        field.valueOrDefault(
          LiftQueryExecutorHelpers.setInstanceFieldFromDbo(
            intermediateRecord,
            document,
            field.field.name
          )
        )
      })

      transformer(selectedFields)
    }

    case None => meta.fromDBObject(document).asInstanceOf[R]
  }

  override def writeToDocument[R <: MongoRecord[_]](record: R): BasicDBObject = {
    record.asDBObject.asInstanceOf[BasicDBObject]
  }
}
