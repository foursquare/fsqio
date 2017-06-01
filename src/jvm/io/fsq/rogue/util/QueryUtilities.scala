// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.util


trait QueryUtilities[Result[_]] {
  def config: QueryConfig
  def logger: QueryLogger[Result]
  def transformer: QueryTransformer
  def validator: QueryValidator
}

class DefaultQueryUtilities[Result[_]] extends QueryUtilities[Result] {
  override val config: QueryConfig = new DefaultQueryConfig
  override val logger: QueryLogger[Result] = new DefaultQueryLogger[Result]
  override val transformer: QueryTransformer = new DefaultQueryTransformer
  override val validator: QueryValidator = new DefaultQueryValidator
}
