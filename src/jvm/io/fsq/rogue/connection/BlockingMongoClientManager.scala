// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.connection

import com.mongodb.{MongoClient, ReadPreference, WriteConcern}
import com.mongodb.client.{MongoCollection, MongoDatabase}
import io.fsq.common.scala.Identity._
import org.bson.codecs.configuration.CodecRegistry

/** MongoConnectionManager for the old blocking client. */
class BlockingMongoClientManager extends MongoClientManager[MongoClient, MongoDatabase, MongoCollection] {

  override protected def closeClient(client: MongoClient): Unit = {
    client.close()
  }

  override protected def getCodecRegistry(db: MongoDatabase): CodecRegistry = {
    db.getCodecRegistry
  }

  override protected def getDatabase(client: MongoClient, name: String): MongoDatabase = {
    client.getDatabase(name)
  }

  override protected def getCollection[Document](
    db: MongoDatabase,
    name: String,
    documentClass: Class[Document],
    readPreferenceOpt: Option[ReadPreference],
    writeConcernOpt: Option[WriteConcern]
  ): MongoCollection[Document] = {
    db.getCollection(name, documentClass)
      .applyOpt(readPreferenceOpt)(_.withReadPreference(_))
      .applyOpt(writeConcernOpt)(_.withWriteConcern(_))
  }
}
