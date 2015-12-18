// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.runtime

trait UntypedMetaRecord {
  def recordName: String
  def annotations: Annotations
  def createUntypedRawRecord: UntypedRecord
  def untypedFields: Seq[UntypedFieldDescriptor]
  def untypedIfInstanceFrom(x: AnyRef): Option[UntypedRecord]
}

trait MetaRecord[R <: Record[R], M <: MetaRecord[R, M]] extends UntypedMetaRecord {
  def createRecord: R
  def createRawRecord: MutableRecord[R]
  def fields: Seq[FieldDescriptor[_, R, M]]
  def ifInstanceFrom(x: AnyRef): Option[R]
}

object MetaRecord {
  def apply[T](implicit c: CompanionProvider[T]): c.CompanionT = c.provide
}
