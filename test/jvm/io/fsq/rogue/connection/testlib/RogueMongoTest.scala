// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.connection.testlib

import com.mongodb.{ConnectionString, MongoClient => BlockingMongoClient, MongoClientURI}
import com.mongodb.async.client.{MongoClient => AsyncMongoClient, MongoClientSettings,
    MongoClients => AsyncMongoClients}
import com.mongodb.connection.ClusterSettings


object RogueMongoTest {

  /* NOTE(jacob): For whatever reason, the default CodecRegistry used by the async client
   *    is exactly the same as the blocking client except for the fact that it omits the
   *    DBObjectCodecProvider, which we need until MongoBuilder has been rewritten to use
   *    something else.
   *
   * TODO(jacob): Remove the custom settings here once MongoBuilder no longer uses
   *    DBObject.
   */
  def buildMongoClients(mongoAddress: String): (AsyncMongoClient, BlockingMongoClient) = {
    val connectionString = new ConnectionString(mongoAddress)
    val asyncMongoClientSettings = {
      MongoClientSettings.builder
        .codecRegistry(
          BlockingMongoClient.getDefaultCodecRegistry
        ).clusterSettings(
          ClusterSettings.builder
            .applyConnectionString(connectionString)
            .build()
        ).build()
    }

    val asyncClient = AsyncMongoClients.create(asyncMongoClientSettings)
    val blockingClient = new BlockingMongoClient(new MongoClientURI(mongoAddress))

    (asyncClient, blockingClient)
  }

  val (asyncMongoClient, blockingMongoClient) = {
    val mongoAddress = {
      val address = Option(System.getProperty("default.mongodb.server")).getOrElse("mongodb://localhost")
      if (!address.startsWith("mongodb://")) {
        s"mongodb://$address"
      } else {
        address
      }
    }

    buildMongoClients(mongoAddress)
  }
}

/** An extension of the MongoTest harness for use by Rogue's own tests. */
trait RogueMongoTest extends MongoTest {
  def asyncMongoClient: AsyncMongoClient = RogueMongoTest.asyncMongoClient
  def blockingMongoClient: BlockingMongoClient = RogueMongoTest.blockingMongoClient
}
