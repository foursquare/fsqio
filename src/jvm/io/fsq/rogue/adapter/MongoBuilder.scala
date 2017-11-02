// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.adapter

import io.fsq.rogue.Query
import org.bson.{BsonDocument, BsonInt32, BsonString}
import org.bson.conversions.Bson


/** A utility helper to build various query documents used by MongoClientAdapter and
  * elsewhere. */
object MongoBuilder {
  def buildQueryModifiers(query: Query[_, _, _]): Bson = {
    val modifiers = new BsonDocument
    query.comment.foreach(comment => modifiers.put("$comment", new BsonString(comment)))
    query.maxScan.foreach(maxScan => modifiers.put("$maxScan", new BsonInt32(maxScan)))

    query.hint.foreach(hint => {
      val hintDocument = new BsonDocument
      hint.asListMap.foreach({
        case (fieldName, intModifier: Int) => {
          hintDocument.put(fieldName, new BsonInt32(intModifier))
        }
        case (fieldName, stringModifier: String) => {
          hintDocument.put(fieldName, new BsonString(stringModifier))
        }
        case (fieldName, wronglyTypedModifier) => {
          throw new RuntimeException(s"Found index modifier of unsupported type: $wronglyTypedModifier")
        }
      })
      modifiers.put("$hint", hintDocument)
    })

    modifiers
  }
}
