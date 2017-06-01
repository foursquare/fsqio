// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.util

import io.fsq.rogue.Query
import io.fsq.rogue.index.UntypedMongoIndex


trait QueryLogger[Result[_]] {
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
    f: => Result[T]
  ): Result[T]

  def onExecuteWriteCommand[T](
    operationName: String,
    collectionName: String,
    instanceName: String,
    msg: => String,
    f: => Result[T]
  ): Result[T]

  def logIndexMismatch(query: Query[_, _, _], msg: => String)

  def logIndexHit(query: Query[_, _, _], index: UntypedMongoIndex)

  def warn(query: Query[_, _, _], msg: => String): Unit
}

class DefaultQueryLogger[Result[_]] extends QueryLogger[Result] {
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
    f: => Result[T]
  ): Result[T] = f

  override def onExecuteWriteCommand[T](
    operationName: String,
    collectionName: String,
    instanceName: String,
    msg: => String,
    f: => Result[T]
  ): Result[T] = f

  override def logIndexMismatch(query: Query[_, _, _], msg: => String): Unit = ()

  override def logIndexHit(query: Query[_, _, _], index: UntypedMongoIndex): Unit = ()

  override def warn(query: Query[_, _, _], msg: => String): Unit = ()
}
