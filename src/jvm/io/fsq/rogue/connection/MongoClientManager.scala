// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.connection

import com.mongodb.{MongoException, ReadPreference, WriteConcern}
import java.util.concurrent.ConcurrentHashMap
import org.bson.codecs.configuration.CodecRegistry
import scala.collection.JavaConverters.mapAsScalaConcurrentMapConverter
import scala.collection.concurrent.{Map => ConcurrentMap}


/** Manages mongo connections and provides access to the client objects. This class is
  * modeled after lift's MongoDB singleton, but in a way that abstracts out the type of
  * client used (async vs blocking). Users must implement closeClient, getCodecRegistry,
  * getDatabase, and getCollection.
  */
abstract class MongoClientManager[MongoClient, MongoDatabase, MongoCollection[_]] {

  private val dbs: ConcurrentMap[MongoIdentifier, (MongoClient, String)] = {
    new ConcurrentHashMap[MongoIdentifier, (MongoClient, String)].asScala
  }

  /** Close a client connection, without removing it from the internal map. */
  protected def closeClient(client: MongoClient): Unit

  /** Get a CodecRegistry from a MongoDatabase. */
  protected def getCodecRegistry(db: MongoDatabase): CodecRegistry

  /** Get a MongoDatabase from a MongoClient. */
  protected def getDatabase(client: MongoClient, name: String): MongoDatabase

  /** Get a MongoCollection from a MongoDatabase.
    * TODO(jacob): We should get rid of the option to send down a read preference here and
    *     just use the one on the query.
    */
  protected def getCollection[Document](
    db: MongoDatabase,
    name: String,
    documentClass: Class[Document],
    readPreferenceOpt: Option[ReadPreference],
    writeConcernOpt: Option[WriteConcern]
  ): MongoCollection[Document]

  def defineDb(
    name: MongoIdentifier,
    client: MongoClient,
    dbName: String
  ): Option[(MongoClient, String)] = {
    dbs.put(name, (client, dbName))
  }

  def getCodecRegistryOrThrow(name: MongoIdentifier): CodecRegistry = {
    getCodecRegistry(getDbOrThrow(name))
  }

  def getDb(name: MongoIdentifier): Option[MongoDatabase] = {
    dbs.get(name).map({
      case (client, dbName) => getDatabase(client, dbName)
    })
  }

  def getDbOrThrow(name: MongoIdentifier): MongoDatabase = {
    getDb(name).getOrElse(throw new MongoException(s"Mongo not found: $name"))
  }

  /** Get a set of all connection ids handled by this client manager. */
  def getConnectionIds: Set[MongoIdentifier] = dbs.keySet.toSet

  /** Executes the given function with the specified database. Throws if the database does
    * not exist. */
  def use[T](name: MongoIdentifier)(f: MongoDatabase => T): T = f(getDbOrThrow(name))

  /** Executes the given function with the specified database and collection. Throws if
    * the database does not exist. */
  def useCollection[
    Document,
    T
  ](
    name: MongoIdentifier,
    collectionName: String,
    documentClass: Class[Document],
    readPreferenceOpt: Option[ReadPreference] = None,
    writeConcernOpt: Option[WriteConcern] = None
  )(
    f: MongoCollection[Document] => T
  ): T = {
    use(name)(db => {
      val collection = getCollection(
        db,
        collectionName,
        documentClass,
        readPreferenceOpt,
        writeConcernOpt
      )
      f(collection)
    })
  }

  /** Close all clients and clear the internal map. */
  def closeAll(): Unit = {
    dbs.valuesIterator.foreach({
      case (client, _) => closeClient(client)
    })
    dbs.clear()
  }
}
