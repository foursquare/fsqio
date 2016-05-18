// Copyright 2016 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.hfile.reader.concrete.mmap

import java.nio.ByteBuffer

class Version(trailerBuffer: ByteBuffer) {
  val versionIndex = (trailerBuffer.limit - 4).toInt
  val version = trailerBuffer.getInt(versionIndex)
  val majorVersion = version & 0x00ffffff
  val minorVersion = version >> 24
}
