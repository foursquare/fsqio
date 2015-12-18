// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.actions

import com.twitter.util.Future
import io.fsq.exceptionator.filter.ProcessedIncoming

trait HasBackgroundActions {
  def backgroundActions: BackgroundActions
}

trait BackgroundAction {
  def postSave(processedIncoming: ProcessedIncoming): Future[Unit]
}

trait BackgroundActions {
  def postSave(processedIncoming: ProcessedIncoming): List[Future[Unit]]
}

