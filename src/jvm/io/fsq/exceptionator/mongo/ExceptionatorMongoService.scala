// Copyright 2018 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.mongo

import com.mongodb.{BasicDBObject, MongoClient}
import com.mongodb.client.{MongoCollection, MongoDatabase}
import io.fsq.rogue.adapter.BlockingResult
import io.fsq.rogue.query.QueryExecutor
import io.fsq.spindle.rogue.adapter.SpindleMongoCollectionFactory
import io.fsq.spindle.runtime.{UntypedMetaRecord, UntypedRecord}

trait ExceptionatorMongoService {
  def executor: Executor
  def collectionFactory: CollectionFactory
  type Executor = QueryExecutor[
    MongoCollection,
    Object,
    BasicDBObject,
    UntypedMetaRecord,
    UntypedRecord,
    BlockingResult
  ]

  type CollectionFactory = SpindleMongoCollectionFactory[
    MongoClient,
    MongoDatabase,
    MongoCollection
  ]
}

trait HasExceptionatorMongoService {
  def exceptionatorMongoService: ExceptionatorMongoService
}
