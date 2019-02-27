// Copyright 2018 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.mongo.concrete

import io.fsq.exceptionator.mongo.ExceptionatorMongoService
import io.fsq.rogue.QueryOptimizer
import io.fsq.rogue.adapter.{BlockingMongoClientAdapter, BlockingResult}
import io.fsq.rogue.connection.BlockingMongoClientManager
import io.fsq.rogue.query.QueryExecutor
import io.fsq.rogue.util.{DefaultQueryConfig, DefaultQueryUtilities, QueryConfig}
import io.fsq.spindle.rogue.adapter.SpindleMongoCollectionFactory
import io.fsq.spindle.rogue.query.SpindleRogueSerializer

object ConcreteExceptionatorMongoService {
  val defaultDbSocketTimeoutMS: Long = 10 * 1000L
}

class ConcreteExceptionatorMongoService extends ExceptionatorMongoService {
  override lazy val clientManager = new BlockingMongoClientManager

  override lazy val collectionFactory: ExceptionatorMongoService.CollectionFactory = {
    new SpindleMongoCollectionFactory(clientManager)
  }

  override lazy val executor: ExceptionatorMongoService.Executor = {
    val queryUtilities = new DefaultQueryUtilities[BlockingResult] {
      override val config: QueryConfig = new DefaultQueryConfig {
        // Configure maxTimeMS in Rogue to kill queries in mongo after a socket timeout
        override def maxTimeMSOpt(configName: String): Option[Long] = {
          Some(ConcreteExceptionatorMongoService.defaultDbSocketTimeoutMS)
        }
      }
    }
    val clientAdapter = new BlockingMongoClientAdapter(collectionFactory, queryUtilities)
    val optimizer = new QueryOptimizer
    val serializer = new SpindleRogueSerializer

    new QueryExecutor(clientAdapter, optimizer, serializer)
  }
}
