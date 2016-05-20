// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.spindle

import io.fsq.rogue.{ModifyQuery, Query}
import io.fsq.spindle.runtime.{UntypedMetaRecord, UntypedRecord}

sealed trait BulkOperation[M <: UntypedMetaRecord, R <: UntypedRecord]
case class BulkInsertOne[M <: UntypedMetaRecord, R <: UntypedRecord](record: R) extends BulkOperation[M, R] {}
case class BulkRemoveOne[M <: UntypedMetaRecord, R <: UntypedRecord](query: Query[M, R, _]) extends BulkOperation[M, R]
case class BulkRemove[M <: UntypedMetaRecord, R <: UntypedRecord](query: Query[M, R, _]) extends BulkOperation[M, R]
case class BulkReplaceOne[M <: UntypedMetaRecord, R <: UntypedRecord](query: Query[M, R, _], record: R, upsert: Boolean) extends BulkOperation[M, R]
case class BulkUpdateOne[M <: UntypedMetaRecord, R <: UntypedRecord](mod: ModifyQuery[M, _], upsert: Boolean) extends BulkOperation[M, R]
case class BulkUpdate[M <: UntypedMetaRecord, R <: UntypedRecord](mod: ModifyQuery[M, _], upsert: Boolean) extends BulkOperation[M, R]