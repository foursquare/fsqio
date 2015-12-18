// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.lift

import com.mongodb.DBObject
import net.liftweb.json.{Extraction, Formats, Serializer, TypeInfo}
import net.liftweb.json.JsonAST.{JObject, JValue}
import net.liftweb.mongodb.{JObjectParser, ObjectIdSerializer}

object LiftQueryHelpers {

  class DBObjectSerializer extends Serializer[DBObject] {
    val DBObjectClass = classOf[DBObject]

    def deserialize(implicit formats: Formats): PartialFunction[(TypeInfo, JValue), DBObject] = {
      case (TypeInfo(klass, _), json : JObject) if DBObjectClass.isAssignableFrom(klass) =>
        JObjectParser.parse(json)
    }

    def serialize(implicit formats: Formats): PartialFunction[Any, JValue] = {
      case x: DBObject =>
        JObjectParser.serialize(x)
    }
  }

  private implicit val formats =
    (net.liftweb.json.DefaultFormats + new ObjectIdSerializer + new DBObjectSerializer)

  def asDBObject[T](x: T): DBObject = {
    JObjectParser.parse(Extraction.decompose(x).asInstanceOf[JObject])
  }
}
