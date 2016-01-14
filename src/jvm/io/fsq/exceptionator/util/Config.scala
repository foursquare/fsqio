// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.util

import com.typesafe.config.{ConfigException, ConfigFactory, ConfigRenderOptions}
import java.io.File

object Config extends Logger {
  def defaultInit() {
    val configFile = new File(System.getProperty("config", "config.json"))
    config = Some(if (configFile.exists) {
      logger.info("Using config from " + configFile.toString)
      ConfigFactory.parseFile(configFile).withFallback(ConfigFactory.load())
    } else {
      val configResource = "io/fsq/exceptionator/config/config.json"
      logger.warning("No config specified, loading defaults from /io/fsq/exceptionator/config/config.json")
      ConfigFactory.load(configResource)
    })
  }
  var config: Option[com.typesafe.config.Config] = None
  def root: com.typesafe.config.Config = config.getOrElse(throw new ConfigException.Missing("No config loaded!"))
  def opt[T](config: com.typesafe.config.Config, f: com.typesafe.config.Config => T): Option[T] = {
    try {
      Some(f(config))
    } catch {
      case e: ConfigException.Missing =>
        None
      case e: ConfigException =>
        logger.warning(e.getMessage)
        None
    }
  }
  def opt[T](f: com.typesafe.config.Config => T): Option[T] = opt(root, f)

  def renderJson(config: com.typesafe.config.Config, path: String): Option[String] = {
    opt(config, _.getValue(path)).map(_.render(ConfigRenderOptions.concise))
  }
  def renderJson(path: String): Option[String] = renderJson(root, path)
}

