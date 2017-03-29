// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.adapter

import com.mongodb.Block
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.CountOptions
import org.bson.BsonValue
import org.bson.conversions.Bson


object BlockingResult {
  trait Implicits {
    implicit def wrap[T](value: T): BlockingResult[T] = new BlockingResult[T](value)
    implicit def unwrap[T](wrapped: BlockingResult[T]): T = wrapped.unwrap
  }

  object Implicits extends Implicits
}

// TODO(jacob): Currently this can't be an AnyVal due to erasure causing a clash in the
//    wrapEmptyResult method below, see if there is a workaround.
class BlockingResult[T](val value: T) {
  def unwrap: T = value
}


object BlockingMongoClientAdapter {
  type CollectionFactory[
    DocumentValue,
    Document <: java.util.Map[String, DocumentValue],
    MetaRecord,
    Record
  ] = MongoCollectionFactory[
    MongoCollection,
    DocumentValue,
    Document,
    MetaRecord,
    Record
  ]
}

class BlockingMongoClientAdapter[
  DocumentValue,
  Document <: java.util.Map[String, DocumentValue],
  MetaRecord,
  Record,
  Result[_]
](
  collectionFactory: BlockingMongoClientAdapter.CollectionFactory[DocumentValue, Document, MetaRecord, Record]
) extends MongoClientAdapter[MongoCollection, DocumentValue, Document, MetaRecord, Record, BlockingResult](
  collectionFactory
) with BlockingResult.Implicits {

  override def wrapEmptyResult[T](value: T): BlockingResult[T] = new BlockingResult[T](value)

  override protected def getCollectionName(collection: MongoCollection[Document]): String = {
    collection.getNamespace.getCollectionName
  }

  override protected def countImpl(
    collection: MongoCollection[Document]
  )(
    filter: Bson,
    options: CountOptions
  ): BlockingResult[Long] = {
    collection.count(filter, options)
  }

  override protected def distinctImpl[T](
    resultAccessor: => T, // call by name
    accumulator: Block[BsonValue]
  )(
    collection: MongoCollection[Document]
  )(
    fieldName: String,
    filter: Bson
  ): BlockingResult[T] = {
    collection.distinct(fieldName, filter, classOf[BsonValue]).forEach(accumulator)
    resultAccessor
  }

  override protected def insertImpl[R <: Record](
    collection: MongoCollection[Document]
  )(
    record: R,
    document: Document
  ): BlockingResult[R] = {
    collection.insertOne(document)
    record
  }
}
