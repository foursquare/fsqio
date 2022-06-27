// Copyright 2022 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.shard

/** Temporarily used to parse shard keys from Lift models when calling `save` in Rogue on a sharded collection. Should
  * no longer be needed once all `save` calls for Lift models with non `_id` shard keys are converted to Thrift.
  */
trait ShardedCollection[T] {

  /** Name of the field that the collection is sharded by */
  def shardKey: String
}