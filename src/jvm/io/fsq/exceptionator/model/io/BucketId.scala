// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.
package io.fsq.exceptionator.model.io

object BucketId {
  def apply(id: String): BucketId = id.split(":") match {
    case Array(name, key) => BucketId(name, key, None)
  }

  def apply(id: String, count: Int): BucketId =
    apply(id).count(count)
}

case class BucketId(name: String, key: String, count: Option[Int] = None) {
  def count(notices: Int) = copy(count = Some(notices))
  override def toString = "%s:%s".format(name, key)
}
