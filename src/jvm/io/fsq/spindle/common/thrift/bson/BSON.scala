// Copyright 2015 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.spindle.common.thrift.bson

// BSON field type constants
object BSON {
  val EOO = 0.toByte
  val NUMBER = 1.toByte
  val STRING = 2.toByte
  val OBJECT = 3.toByte
  val ARRAY = 4.toByte
  val BINARY = 5.toByte
  val UNDEFINED = 6.toByte
  val OID = 7.toByte
  val BOOLEAN = 8.toByte
  val DATE = 9.toByte
  val NULL = 10.toByte
  val REGEX = 11.toByte
  val REF = 12.toByte
  val CODE = 13.toByte
  val SYMBOL = 14.toByte
  val CODE_W_SCOPE = 15.toByte
  val NUMBER_INT = 16.toByte
  val TIMESTAMP = 17.toByte
  val NUMBER_LONG = 18.toByte

  val MINKEY = -1.toByte;
  val MAXKEY = 127.toByte
}
