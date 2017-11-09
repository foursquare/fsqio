// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.twofishes.indexer.mongo

import io.fsq.twofishes.model.gen.{ThriftPolygonIndex, ThriftPolygonIndexProxy}
import java.nio.ByteBuffer
import org.bson.types.ObjectId

class PolygonIndex(override val underlying: ThriftPolygonIndex) extends ThriftPolygonIndexProxy

object PolygonIndex {
  def apply(
    id: ObjectId,
    polygon: Array[Byte],
    source: String
  ): PolygonIndex = {
    val thriftModel = ThriftPolygonIndex.newBuilder
      .id(id)
      .polygon(ByteBuffer.wrap(polygon))
      .source(source)
      .result()
    new PolygonIndex(thriftModel)
  }
}
