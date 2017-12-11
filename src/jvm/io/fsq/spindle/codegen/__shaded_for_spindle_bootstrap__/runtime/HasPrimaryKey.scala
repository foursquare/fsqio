// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.__shaded_for_spindle_bootstrap__.runtime

trait SemitypedHasPrimaryKey[F] {
  def primaryKey: F
}

trait HasPrimaryKey[F, R <: Record[R]] extends SemitypedHasPrimaryKey[F]

trait HasMetaPrimaryKey[F, R <: Record[R]] {
  def primaryKey: FieldDescriptor[F, R, _ <: MetaRecord[R, _]]
}
