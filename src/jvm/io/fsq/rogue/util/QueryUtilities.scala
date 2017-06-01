// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.util


trait QueryUtilities {
  def config: QueryConfig
  def logger: QueryLogger
  def transformer: QueryTransformer
  def validator: QueryValidator
}

class DefaultQueryUtilities extends QueryUtilities {
  override val config: QueryConfig = new DefaultQueryConfig
  override val logger: QueryLogger = new DefaultQueryLogger
  override val transformer: QueryTransformer = new DefaultQueryTransformer
  override val validator: QueryValidator = new DefaultQueryValidator
}
