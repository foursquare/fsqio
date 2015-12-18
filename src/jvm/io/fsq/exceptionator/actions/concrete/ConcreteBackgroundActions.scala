// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.actions.concrete

import com.twitter.util.Future
import io.fsq.exceptionator.actions.{BackgroundAction, BackgroundActions}
import io.fsq.exceptionator.filter.ProcessedIncoming
import io.fsq.exceptionator.loader.service.HasPluginLoaderService
import io.fsq.exceptionator.util.Config
import scala.collection.JavaConverters._


class ConcreteBackgroundActions(
  services: HasPluginLoaderService) extends BackgroundActions {

  val actions = services.pluginLoader.serviceConstruct[BackgroundAction](
    Config.root.getStringList("incoming.postSaveActions").asScala)

  def postSave(processedIncoming: ProcessedIncoming): List[Future[Unit]] = {
    actions.map(_.postSave(processedIncoming)).toList
  }}
