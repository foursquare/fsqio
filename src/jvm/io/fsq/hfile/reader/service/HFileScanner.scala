package io.fsq.hfile.reader.service

import java.nio.ByteBuffer

// https://hbase.apache.org/apidocs/org/apache/hadoop/hbase/io/hfile/HFileScanner.html
trait HFileScanner {
  // Gets a buffer view to the current key. A seek or rewind must be have been called before this.
  def getKey(): ByteBuffer
  // Gets a buffer view to the current value. A seek or rewind must be have been called before this.
  def getValue(): ByteBuffer

  // Scans to the next entry in the file, returning true if one is found.
  def next(): Boolean

  // Seeks from the beginning of the file to, or to just before, the requested key.
  //
  // Returns:
  //  0 if the passed key was found
  //    - the scanner is now at the first occurrence of the passed key.
  //  -1 if the passed key is less than the first key in the file
  //    - the scanner's position is undefined.
  //  1 if the passed key was not found
  //    - the scanner is now positioned at the greatest key that is less than the passed key.
  def seekTo(key: ByteBuffer): Int

  // same as `reseekTo(key)` except the key is broken down into chunks
  def seekTo(splitKey: Seq[ByteBuffer]): Int

  // Same as `seekTo(key)` except only seeks forward from current position (potentially useful for perf reasons).
  def reseekTo(key: ByteBuffer): Int

  // same as `reseekTo(key)` except the key is broken down into chunks
  def reseekTo(splitKey: Seq[ByteBuffer]): Int

  // Position the scanner at the beginning of the file and return true if the file is non-empty.
  def rewind(): Boolean

  def isSeeked(): Boolean
}
