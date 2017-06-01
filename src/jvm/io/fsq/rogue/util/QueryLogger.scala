// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.util

import io.fsq.rogue.Query
import io.fsq.rogue.index.UntypedMongoIndex


trait QueryLogger {
  def logCounter(nameParts: String*)(count: Int): Unit

  def log(
    query: Query[_, _, _],
    instanceName: String,
    msg: => String,
    timeMillis: Long
  ): Unit

  def onExecuteQuery[T](
    query: Query[_, _, _],
    instanceName: String,
    msg: => String,
    func: => T
  ): T

  def onExecuteWriteCommand[T](
    operationName: String,
    collectionName: String,
    instanceName: String,
    msg: => String,
    func: => T
  ): T

  def logIndexMismatch(query: Query[_, _, _], msg: => String)

  def logIndexHit(query: Query[_, _, _], index: UntypedMongoIndex)

  def warn(query: Query[_, _, _], msg: => String): Unit
}

class DefaultQueryLogger extends QueryLogger {
  override def logCounter(nameParts: String*)(count: Int): Unit = ()

  override def log(
    query: Query[_, _, _],
    instanceName: String,
    msg: => String,
    timeMillis: Long
  ): Unit = ()

  override def onExecuteQuery[T](
    query: Query[_, _, _],
    instanceName: String,
    msg: => String,
    f: => T
  ): T = f

  override def onExecuteWriteCommand[T](
    operationName: String,
    collectionName: String,
    instanceName: String,
    msg: => String,
    f: => T
  ): T = f

  override def logIndexMismatch(query: Query[_, _, _], msg: => String): Unit = ()

  override def logIndexHit(query: Query[_, _, _], index: UntypedMongoIndex): Unit = ()

  override def warn(query: Query[_, _, _], msg: => String): Unit = ()
}
