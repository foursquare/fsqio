// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.util

import io.fsq.common.logging.Logger

// TODO: make this useful someday
object PluginLoader extends Logger {
  def defaultConstruct[T](classNames: Seq[String])(implicit man: Manifest[T]): Seq[T] = {
    val classLoader = getClass.getClassLoader
    classNames.map(className => {
      logger.info("Loading %s: %s".format(man.runtimeClass.getSimpleName, className))
      classLoader.loadClass(className).newInstance.asInstanceOf[T]
    })
  }
}
