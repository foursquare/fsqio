// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.hfile.reader.concrete.mmap

// hbase-0.94.6-cdh4.4.0-sources/org/apache/hadoop/hbase/util/ClassSize.java
object ClassSize {
  val REFERENCE = 8
  val ARRAY = align(3 * REFERENCE)
  val OBJECT = 2 * REFERENCE

  def align(num: Int): Int = {
    align(num.toLong).toInt
  }

  def align(num: Long): Long = {
    (((num + 7) >> 3) << 3)
  }

  def is32BitJVM(): Boolean = {
    System.getProperty("sun.arch.data.model").equals("32");
  }
}
