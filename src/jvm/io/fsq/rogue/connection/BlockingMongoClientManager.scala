// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.connection

import com.mongodb.{MongoClient, ReadPreference}
import com.mongodb.client.{MongoCollection, MongoDatabase}


/** MongoConnectionManager for the old blocking client. */
class BlockingMongoClientManager
extends MongoClientManager[MongoClient, MongoDatabase, MongoCollection] {

  override protected def closeClient(client: MongoClient): Unit = {
    client.close()
  }

  override protected def getDatabase(client: MongoClient, name: String): MongoDatabase = {
    client.getDatabase(name)
  }

  override protected def getCollection[Document](
    db: MongoDatabase,
    name: String,
    documentClass: Class[Document],
    readPreferenceOpt: Option[ReadPreference]
  ): MongoCollection[Document] = {
    val collection = db.getCollection(name, documentClass)
    readPreferenceOpt.map(collection.withReadPreference(_)).getOrElse(collection)
  }
}
