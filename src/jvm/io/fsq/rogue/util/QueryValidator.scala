// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.util

import io.fsq.rogue.{Degrees, FindAndModifyQuery, ModifyQuery, Query}
import io.fsq.rogue.index.UntypedMongoIndex

trait QueryValidator {
  def validateList[T](xs: Traversable[T]): Unit

  def validateRadius(d: Degrees): Degrees

  def validateQuery[M](
    query: Query[M, _, _],
    indexesOpt: Option[Seq[UntypedMongoIndex]]
  ): Unit

  def validateModify[M](
    modify: ModifyQuery[M, _],
    indexesOpt: Option[Seq[UntypedMongoIndex]]
  ): Unit

  def validateFindAndModify[M, R](
    modify: FindAndModifyQuery[M, R],
    indexesOpt: Option[Seq[UntypedMongoIndex]]
  ): Unit
}

class DefaultQueryValidator extends QueryValidator {
  override def validateList[T](xs: Traversable[T]): Unit = ()

  override def validateRadius(degrees: Degrees): Degrees = degrees

  override def validateQuery[M](
    query: Query[M, _, _],
    indexesOpt: Option[Seq[UntypedMongoIndex]]
  ): Unit = ()

  override def validateModify[M](
    modify: ModifyQuery[M, _],
    indexesOpt: Option[Seq[UntypedMongoIndex]]
  ): Unit = ()

  override def validateFindAndModify[M, R](
    modify: FindAndModifyQuery[M, R],
    indexesOpt: Option[Seq[UntypedMongoIndex]]
  ): Unit = ()
}
