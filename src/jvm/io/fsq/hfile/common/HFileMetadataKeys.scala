// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.hfile.common


trait HFileMetadataKeys {
  val ThriftClassKey: String = "key.thrift.class"
  val ThriftClassValue: String = "value.thrift.class"
  val ThriftEncodingKey: String = "thrift.protocol.factory.class"
  val TimestampKey: String = "generation.millis"
  val TaskIdKey: String = "generation.taskId"

  val LastKeyKey: String = "hfile.LASTKEY"
  val AverageKeyLengthKey = "hfile.AVG_KEY_LEN"
  val AverageValueLengthKey = "hfile.AVG_VALUE_LEN"
  val ComparatorKey = "hfile.COMPARATOR"
  val NumEntries = "hfile.NUM_ENTRIES"
  val NumUniqueKeys = "hfile.NUM_UNIQUE_KEYS"
  val TotalKeyLength = "hfile.TOTAL_KEY_LENGTH"
  val TotalValueLength = "hfile.TOTAL_VALUE_LENGTH"
}

object HFileMetadataKeys extends HFileMetadataKeys
