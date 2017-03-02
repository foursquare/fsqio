// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.index

/**
 * A trait that represents the fact that a record type includes a list
 * of the indexes that exist in MongoDB for that type.
 *
 * TODO(jacob): This type parameter is unnecessary.
 */
trait IndexedRecord[M] {
  val mongoIndexList: Seq[MongoIndex[_]] = Vector.empty
}
