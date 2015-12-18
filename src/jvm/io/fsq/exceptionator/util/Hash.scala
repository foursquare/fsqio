// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.exceptionator.util

import java.security.MessageDigest

object Hash {
  val md5 = MessageDigest.getInstance("MD5")
  val empty = Hash.ofString("")
  val fieldNames = (('0' to '9') ++ ('A' to 'Z') ++ ('a' to 'z')).toArray
  def hexEncode(bytes: Array[Byte]) = bytes.map(b => "%02x" format b).mkString
  def ofString(s: String) = hexEncode(md5.digest(s.getBytes("utf-8")))
  def ofList(strings: List[String]) = ofString(strings.mkString(";"))
  def fieldNameEncode(num: Int): String = {
    if (num >= fieldNames.length) {
      throw new IllegalArgumentException("num %d is out of range".format(num))
    } else {
      fieldNames(num).toString
    }
  }
  def fieldNameDecode(name: String): Int = {
    if (name.length > 1) {
      throw new IllegalArgumentException("name %s is out of range".format(name))
    }
    fieldNames.indexOf(name(0))
  }
}
