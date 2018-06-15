// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.util

import io.fsq.rogue.{FindAndModifyQuery, ModifyQuery, Query}

trait QueryTransformer {
  def transformQuery[M](query: Query[M, _, _]): Query[M, _, _]

  def transformModify[M](modify: ModifyQuery[M, _]): ModifyQuery[M, _]

  def transformFindAndModify[M, R](
    modify: FindAndModifyQuery[M, R]
  ): FindAndModifyQuery[M, R]
}

class DefaultQueryTransformer extends QueryTransformer {
  override def transformQuery[M](query: Query[M, _, _]): Query[M, _, _] = {
    query
  }

  override def transformModify[M](modify: ModifyQuery[M, _]): ModifyQuery[M, _] = {
    modify
  }

  override def transformFindAndModify[M, R](
    findAndModify: FindAndModifyQuery[M, R]
  ): FindAndModifyQuery[M, R] = {
    findAndModify
  }
}
