// Copyright 2014 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.common.scala

import java.net.{MalformedURLException, URI, URISyntaxException, URL}
import org.bson.types.ObjectId

object TryO {

  def catchAll[T](f: => T): Option[T] = try { Some(f) } catch { case _: Exception => None }

  def apply[T](toNone: Class[_]*)(f: => T): Option[T] = try {
    Some(f)
  } catch {
    case e if toNone.exists(_.isAssignableFrom(e.getClass)) => None
  }

  def toInt(str: String): Option[Int] = apply(classOf[NumberFormatException])(str.toInt)
  def toLong(str: String): Option[Long] = apply(classOf[NumberFormatException])(str.toLong)
  def toShort(str: String): Option[Short] = apply(classOf[NumberFormatException])(str.toShort)
  def toDouble(str: String): Option[Double] = apply(classOf[NumberFormatException])(str.toDouble)
  def toBoolean(str: String): Option[Boolean] = apply(classOf[IllegalArgumentException])(str.toBoolean)
  def toUrl(str: String): Option[URL] = apply(classOf[MalformedURLException])(new URL(str))
  def toUri(str: String): Option[URI] = apply(classOf[URISyntaxException])(new URI(str))
  def toObjectId(str: String): Option[ObjectId] = if (ObjectId.isValid(str)) Some(new ObjectId(str)) else None

  object AsInt { def unapply(str: String): Option[Int] = toInt(str) }
  object AsLong { def unapply(str: String): Option[Long] = toLong(str) }
  object AsShort { def unapply(str: String): Option[Short] = toShort(str) }
  object AsDouble { def unapply(str: String): Option[Double] = toDouble(str) }
  object AsBoolean { def unapply(str: String): Option[Boolean] = toBoolean(str) }
  object AsUrl { def unapply(str: String): Option[URL] = toUrl(str) }
  object AsUri { def unapply(str: String): Option[URI] = toUri(str) }
  object AsObjectId { def unapply(str: String): Option[ObjectId] = toObjectId(str) }
}
