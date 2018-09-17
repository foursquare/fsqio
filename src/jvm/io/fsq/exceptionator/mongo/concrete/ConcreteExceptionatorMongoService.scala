// Copyright 2018 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.mongo.concrete

import com.mongodb.MongoClient
import io.fsq.exceptionator.mongo.ExceptionatorMongoService
import io.fsq.rogue.QueryOptimizer
import io.fsq.rogue.adapter.{BlockingMongoClientAdapter, BlockingResult}
import io.fsq.rogue.connection.{BlockingMongoClientManager, DefaultMongoIdentifier}
import io.fsq.rogue.query.QueryExecutor
import io.fsq.rogue.util.DefaultQueryUtilities
import io.fsq.spindle.rogue.adapter.SpindleMongoCollectionFactory
import io.fsq.spindle.rogue.query.SpindleRogueSerializer

class ConcreteExceptionatorMongoService extends ExceptionatorMongoService {
  val clientManager = {
    val manager = new BlockingMongoClientManager
    Runtime
      .getRuntime()
      .addShutdownHook(new Thread() {
        override def run(): Unit = {
          manager.closeAll()
        }
      })
    manager
  }

  private val mongoIdentifier = new DefaultMongoIdentifier("exceptionator")
  val mongoClient = new MongoClient()
  clientManager.defineDb(mongoIdentifier, mongoClient, "exceptionator")

  val queryUtilities = new DefaultQueryUtilities[BlockingResult]()
  val collectionFactory = new SpindleMongoCollectionFactory(clientManager)
  val clientAdapter = new BlockingMongoClientAdapter(collectionFactory, queryUtilities)

  val optimizer = new QueryOptimizer
  val serializer = new SpindleRogueSerializer
  val executor: ExceptionatorMongoService#Executor = new QueryExecutor(clientAdapter, optimizer, serializer)
}
