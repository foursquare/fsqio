// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.util

import com.mongodb.WriteConcern

trait QueryConfig {
  def cursorBatchSize: Option[Option[Int]]
  def defaultWriteConcern: WriteConcern
  def maxTimeMSOpt(configName: String): Option[Long]
}

class DefaultQueryConfig extends QueryConfig {

  /**
    * Batch size to set on the underlying cursor.
    * None = take value from the query if specified
    * Some(None) = never set batch size on the cursor
    * Some(Some(n)) = always set batch size to n
    *
    * TODO(jacob): Just write a real datatype for this instead of nesting options.
    */
  override def cursorBatchSize: Option[Option[Int]] = None
  override def defaultWriteConcern: WriteConcern = WriteConcern.ACKNOWLEDGED
  override def maxTimeMSOpt(configName: String): Option[Long] = None
}
