// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.connection.testlib

import com.mongodb.{ConnectionString, MongoClient => BlockingMongoClient, MongoClientURI}
import com.mongodb.async.client.{MongoClient => AsyncMongoClient, MongoClientSettings,
    MongoClients => AsyncMongoClients}
import com.mongodb.connection.ClusterSettings
import com.mongodb.connection.netty.NettyStreamFactoryFactory
import io.netty.channel.nio.NioEventLoopGroup
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger


object RogueMongoTest {

  /* NOTE(jacob): For whatever reason, the default CodecRegistry used by the async client
   *    is exactly the same as the blocking client except for the fact that it omits the
   *    DBObjectCodecProvider, which we need until MongoBuilder has been rewritten to use
   *    something else.
   *
   * TODO(jacob): Remove the custom settings here once MongoBuilder no longer uses
   *    DBObject.
   */
  def buildAsyncMongoClientSettings(mongoAddress: String): MongoClientSettings = {
    val connectionString = new ConnectionString(mongoAddress)

    val nettyThreadCount = System.getProperty("RogueMongoTest.mongodb.asyncThreads", "4").toInt
    val nettyThreadFactory = new ThreadFactory {
      val threadNumber = new AtomicInteger
      override def newThread(runnable: Runnable): Thread = {
        val thread = new Thread(runnable, s"RogueMongoTest-nettyThread-${threadNumber.incrementAndGet}")
        thread.setDaemon(true)
        thread
      }
    }
    val nettyEventLoop = new NioEventLoopGroup(nettyThreadCount, nettyThreadFactory)

    MongoClientSettings.builder
      .codecRegistry(
        BlockingMongoClient.getDefaultCodecRegistry
      ).clusterSettings(
        ClusterSettings.builder
          .applyConnectionString(connectionString)
          .build()
      ).streamFactoryFactory(
        NettyStreamFactoryFactory.builder
          .eventLoopGroup(nettyEventLoop)
          .build()
      ).build()
  }

  val (asyncMongoClient, blockingMongoClient) = {
    val mongoAddress = {
      val address = System.getProperty("default.mongodb.server", "mongodb://localhost")
      if (!address.startsWith("mongodb://")) {
        s"mongodb://$address"
      } else {
        address
      }
    }

    val asyncClient = AsyncMongoClients.create(buildAsyncMongoClientSettings(mongoAddress))
    val blockingClient = new BlockingMongoClient(new MongoClientURI(mongoAddress))

    (asyncClient, blockingClient)
  }
}

/** An extension of the MongoTest harness for use by Rogue's own tests. */
trait RogueMongoTest extends MongoTest {
  def asyncMongoClient: AsyncMongoClient = RogueMongoTest.asyncMongoClient
  def blockingMongoClient: BlockingMongoClient = RogueMongoTest.blockingMongoClient
}
