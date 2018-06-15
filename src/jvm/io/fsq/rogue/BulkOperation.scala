// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue

sealed trait BulkOperation[MetaRecord, Record] {
  def metaRecord: MetaRecord
}

case class BulkInsertOne[MetaRecord, Record](
  metaRecord: MetaRecord,
  record: Record
) extends BulkOperation[MetaRecord, Record]

sealed trait BulkQueryOperation[MetaRecord, Record] extends BulkOperation[MetaRecord, Record] {
  def query: Query[MetaRecord, Record, _]

  override def metaRecord: MetaRecord = query.meta
}

object BulkQueryOperation {
  def unapply[MetaRecord, Record](
    queryOp: BulkQueryOperation[MetaRecord, Record]
  ): Option[Query[MetaRecord, Record, _]] = {
    Some(queryOp.query)
  }
}

case class BulkRemoveOne[MetaRecord, Record](
  query: Query[MetaRecord, Record, _]
) extends BulkQueryOperation[MetaRecord, Record]

case class BulkRemove[MetaRecord, Record](
  query: Query[MetaRecord, Record, _]
) extends BulkQueryOperation[MetaRecord, Record]

case class BulkReplaceOne[MetaRecord, Record](
  query: Query[MetaRecord, Record, _],
  record: Record,
  upsert: Boolean
) extends BulkQueryOperation[MetaRecord, Record]

sealed trait BulkModifyQueryOperation[MetaRecord, Record] extends BulkOperation[MetaRecord, Record] {
  def modifyQuery: ModifyQuery[MetaRecord, _]
  def multi: Boolean
  def upsert: Boolean

  override def metaRecord: MetaRecord = modifyQuery.query.meta
}

object BulkModifyQueryOperation {
  def unapply[MetaRecord, Record](
    modifyOp: BulkModifyQueryOperation[MetaRecord, Record]
  ): Option[(ModifyQuery[MetaRecord, _], Boolean)] = {
    Some((modifyOp.modifyQuery, modifyOp.upsert))
  }
}

case class BulkUpdateOne[MetaRecord, Record](
  modifyQuery: ModifyQuery[MetaRecord, _],
  upsert: Boolean
) extends BulkModifyQueryOperation[MetaRecord, Record] {
  override val multi: Boolean = false
}

case class BulkUpdateMany[MetaRecord, Record](
  modifyQuery: ModifyQuery[MetaRecord, _],
  upsert: Boolean
) extends BulkModifyQueryOperation[MetaRecord, Record] {
  override val multi: Boolean = true
}
