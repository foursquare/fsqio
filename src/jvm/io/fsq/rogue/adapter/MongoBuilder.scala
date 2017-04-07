// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.adapter

import io.fsq.rogue.Query
import io.fsq.rogue.index.{Asc, Desc, Hashed, TwoD}
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
      hint.asTypedListMap.foreach({
        case (fieldName, intModifier @ (Asc | Desc)) => {
          hintDocument.put(fieldName, new BsonInt32(intModifier.value.asInstanceOf[Int]))
        }
        case (fieldName, stringModifier @ (TwoD | Hashed)) => {
          hintDocument.put(fieldName, new BsonString(stringModifier.value.asInstanceOf[String]))
        }
      })
      modifiers.put("$hint", hintDocument)
    })

    modifiers
  }
}
