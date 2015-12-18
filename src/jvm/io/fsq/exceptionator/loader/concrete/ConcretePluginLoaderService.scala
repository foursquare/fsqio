// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.loader.concrete

import io.fsq.exceptionator.actions.{HasBucketActions, HasNoticeActions, HasUserFilterActions}
import io.fsq.exceptionator.loader.service.PluginLoaderService
import io.fsq.exceptionator.util.Logger

class ConcretePluginLoaderService (
  services: HasBucketActions
    with HasNoticeActions
    with HasUserFilterActions) extends PluginLoaderService with Logger {

  def defaultConstruct[T](classNames: Seq[String])(implicit man: Manifest[T]): Seq[T] = {
    val classLoader = getClass.getClassLoader
    classNames.map(className => {
      logger.info("Loading %s: %s".format(man.runtimeClass.getSimpleName, className))
      classLoader.loadClass(className).newInstance.asInstanceOf[T]
    })
  }

  def serviceConstruct[T](classNames: Seq[String])(implicit man: Manifest[T]): Seq[T] = {
    val classLoader = getClass.getClassLoader
    classNames.flatMap(className => {
      logger.info("Loading %s: %s".format(man.runtimeClass.getSimpleName, className))
      classLoader.loadClass(className).getConstructors.toList.find(_.getParameterTypes match {
        case Array(t1) => {
          t1.isInstance(services)
        }
        case _ => false
      }).map(_.newInstance(services).asInstanceOf[T])
    })
  }
}
