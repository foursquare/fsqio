// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.adapter

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.CountOptions
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
  type CollectionFactory[Document, MetaRecord, Record] = MongoCollectionFactory[
    MongoCollection,
    Document,
    MetaRecord,
    Record
  ]
}

class BlockingMongoClientAdapter[Document, MetaRecord, Record](
  collectionFactory: BlockingMongoClientAdapter.CollectionFactory[Document, MetaRecord, Record]
) extends MongoClientAdapter[MongoCollection, Document, MetaRecord, Record, BlockingResult](
  collectionFactory
) with BlockingResult.Implicits {

  override def wrapEmptyResult[T](value: T): BlockingResult[T] = new BlockingResult[T](value)

  override protected def countImpl(
    collection: MongoCollection[Document]
  )(
    filter: Bson,
    options: CountOptions
  ): BlockingResult[Long] = {
    collection.count(filter, options)
  }
}
