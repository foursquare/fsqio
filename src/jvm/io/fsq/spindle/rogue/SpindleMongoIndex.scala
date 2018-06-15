// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.rogue

import io.fsq.field.Field
import io.fsq.spindle.runtime.{FieldDescriptor, MetaRecord, Record, StructFieldDescriptor}

class SpindleIndexSubField[R <: Record[R], MR <: MetaRecord[R, MR], ER <: Record[ER], EM <: MetaRecord[ER, EM]](
  parent: StructFieldDescriptor[R, MR, ER, EM],
  subField: FieldDescriptor[_, ER, EM]
) extends Field[ER, MR] {
  def name = parent.name + "." + subField.name
  def owner = parent.owner
}
