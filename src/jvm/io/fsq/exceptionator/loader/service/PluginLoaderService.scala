// Copyright 2013 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.loader.service

trait HasPluginLoaderService {
  def pluginLoader: PluginLoaderService
}

trait PluginLoaderService {
  def defaultConstruct[T](classNames: Seq[String])(implicit man: Manifest[T]): Seq[T]
  def serviceConstruct[T](classNames: Seq[String])(implicit man: Manifest[T]): Seq[T]
}
