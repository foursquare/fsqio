// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.query.lift

import com.mongodb.{BasicDBObject, DBObject}
import io.fsq.rogue.MongoHelpers.MongoSelect
import io.fsq.rogue.query.RogueSerializer
import net.liftweb.common.Box
import net.liftweb.mongodb.record.{BsonRecord, MongoMetaRecord, MongoRecord}
import net.liftweb.mongodb.record.field.BsonRecordField
import net.liftweb.record.{Field => LField}
import org.bson.types.BasicBSONList
import scala.collection.JavaConverters.asScalaBufferConverter

class LiftRogueSerializer
  extends RogueSerializer[
    MongoRecord[_] with MongoMetaRecord[_],
    MongoRecord[_],
    BasicDBObject
  ] {

  private def fallbackValueFromDbObject(
    dbo: DBObject,
    fieldNames: Seq[String]
  ): Option[_] = {
    Box
      .!!(fieldNames.foldLeft(dbo: Object)((obj: Object, fieldName: String) => {
        obj match {
          case dbl: BasicBSONList => {
            dbl.asScala.map(_.asInstanceOf[DBObject].get(fieldName))
          }
          case dbo: DBObject => dbo.get(fieldName)
          case null => null
        }
      }))
      .toOption
  }

  private def setFieldFromDbo(
    field: LField[_, _],
    dbo: DBObject,
    fieldNames: Seq[String]
  ): Option[_] = {
    if (field.isInstanceOf[BsonRecordField[_, _]]) {
      val brf = field.asInstanceOf[BsonRecordField[_, _]]
      val inner = brf.value.asInstanceOf[BsonRecord[_]]
      setInstanceFieldFromDboList(inner, dbo, fieldNames)
    } else {
      fallbackValueFromDbObject(dbo, fieldNames)
    }
  }

  private def setInstanceFieldFromDbo(
    instance: MongoRecord[_],
    dbo: DBObject,
    fieldName: String
  ): Option[_] = {
    fieldName.contains(".") match {
      case true => {
        val names = fieldName.split('.').filter(_ != "$")
        setInstanceFieldFromDboList(instance, dbo, names)
      }
      case false => {
        val fld: Box[LField[_, _]] = instance.fieldByName(fieldName)
        fld.flatMap(setLastFieldFromDbo(_, dbo, fieldName))
      }
    }
  }

  private def setInstanceFieldFromDboList(
    instance: BsonRecord[_],
    dbo: DBObject,
    fieldNames: Seq[String]
  ): Option[_] = {
    fieldNames match {
      case Seq(last) => {
        val fld: Box[LField[_, _]] = instance.fieldByName(last)
        fld.flatMap(setLastFieldFromDbo(_, dbo, last))
      }
      case Seq(name, rest @ _*) => {
        val fld: Box[LField[_, _]] = instance.fieldByName(name)
        dbo.get(name) match {
          case obj: DBObject => fld.flatMap(setFieldFromDbo(_, obj, rest))
          case list: BasicBSONList => fallbackValueFromDbObject(dbo, fieldNames)
          case null => None
        }
      }
      case Seq() => {
        throw new UnsupportedOperationException("was called with empty list, shouldn't possibly happen")
      }
    }
  }

  private def setLastFieldFromDbo(
    field: LField[_, _],
    dbo: DBObject,
    fieldName: String
  ): Option[_] = {
    field.setFromAny(dbo.get(fieldName)).toOption
  }

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
          setInstanceFieldFromDbo(
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
