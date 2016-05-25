// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.hfile.writer.service

import io.fsq.common.scala.Lists.Implicits._

trait HFileWriter {
  def addKeyValue(key: Array[Byte], value: Array[Byte]): Unit
  def addFileInfo(key: Array[Byte], value: Array[Byte]): Unit
  def addFileInfo(key: String, value: String): Unit
  def addFileInfo(key: String, value: Long): Unit

  def close(): Unit
}
