// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.util

import io.fsq.rogue.IndexCheckerLogger
import io.fsq.rogue.indexchecker.{IndexChecker, MongoIndexChecker}

trait QueryUtilities[Result[_]] {
  def config: QueryConfig
  def indexChecker: IndexChecker
  def logger: QueryLogger[Result]
  def transformer: QueryTransformer
  def validator: QueryValidator
}

class DefaultQueryUtilities[Result[_]] extends QueryUtilities[Result] { self =>
  override val config: QueryConfig = new DefaultQueryConfig
  override val indexChecker: IndexChecker = new MongoIndexChecker {
    override def logger: IndexCheckerLogger = self.logger
  }
  override val logger: QueryLogger[Result] = new DefaultQueryLogger[Result]
  override val transformer: QueryTransformer = new DefaultQueryTransformer
  override val validator: QueryValidator = new DefaultQueryValidator
}
