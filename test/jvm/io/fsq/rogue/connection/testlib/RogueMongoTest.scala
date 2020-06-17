// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.connection.testlib

import com.mongodb.{
  Block,
  ConnectionString,
  MongoClient => BlockingMongoClient,
  MongoClientOptions,
  MongoClientSettings,
  MongoClientURI,
  ServerAddress
}
import com.mongodb.connection.ClusterSettings
import com.mongodb.connection.netty.NettyStreamFactoryFactory
import com.mongodb.reactivestreams.client.{MongoClient => AsyncMongoClient, MongoClients => AsyncMongoClients}
import io.netty.channel.nio.NioEventLoopGroup
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.JavaConverters.{asScalaBufferConverter, bufferAsJavaListConverter}

object RogueMongoTest {

  val codecRegistry = BlockingMongoClient.getDefaultCodecRegistry

  def buildAsyncMongoClient(mongoAddress: String): AsyncMongoClient = {
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

    val clusterSettingsBlock: Block[ClusterSettings.Builder] = new Block[ClusterSettings.Builder] {
      override def apply(builder: ClusterSettings.Builder): Unit = {
        builder.applyConnectionString(connectionString)
      }
    }

    val settings = {
      MongoClientSettings.builder
        .codecRegistry(
          codecRegistry
        )
        .applyToClusterSettings(
          clusterSettingsBlock
        )
        .streamFactoryFactory(
          NettyStreamFactoryFactory.builder
            .eventLoopGroup(nettyEventLoop)
            .build()
        )
        .build()
    }

    AsyncMongoClients.create(settings)
  }

  def buildBlockingMongoClient(mongoAddress: String): BlockingMongoClient = {
    val mongoClientURI = new MongoClientURI(mongoAddress)

    val hostServerAddresses = mongoClientURI.getHosts.asScala.map(
      hostString =>
        hostString.split(':') match {
          case Array(host, port) => new ServerAddress(host, port.toInt)
          case Array(host) => new ServerAddress(host)
          case _ => throw new IllegalArgumentException(s"Malformed host string: $hostString")
        }
    )

    val clientOptions = {
      new MongoClientOptions.Builder(mongoClientURI.getOptions)
        .codecRegistry(codecRegistry)
        .build()
    }

    new BlockingMongoClient(hostServerAddresses.asJava, clientOptions)
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

    (buildAsyncMongoClient(mongoAddress), buildBlockingMongoClient(mongoAddress))
  }
}

/** An extension of the MongoTest harness for use by Rogue's own tests. */
trait RogueMongoTest extends MongoTest {
  def asyncMongoClient: AsyncMongoClient = RogueMongoTest.asyncMongoClient
  def blockingMongoClient: BlockingMongoClient = RogueMongoTest.blockingMongoClient
}
