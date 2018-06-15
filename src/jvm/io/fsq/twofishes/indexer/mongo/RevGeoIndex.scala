// Copyright 2012 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.twofishes.indexer.mongo

import io.fsq.common.scala.Identity._
import io.fsq.rogue.index.{Desc, MongoIndex}
import io.fsq.twofishes.gen.GeocodePoint
import io.fsq.twofishes.model.gen.{ThriftRevGeoIndex, ThriftRevGeoIndexMeta, ThriftRevGeoIndexProxy}
import java.nio.ByteBuffer
import org.bson.types.ObjectId

class RevGeoIndex(override val underlying: ThriftRevGeoIndex) extends ThriftRevGeoIndexProxy {
  def getGeocodePoint: Option[GeocodePoint] = {
    pointOption.map({
      case Seq(lat, lng) => {
        GeocodePoint.newBuilder.lat(lat).lng(lng).result
      }
    })
  }
}

object RevGeoIndex {
  def apply(
    cellid: Long,
    polyId: ObjectId,
    full: Boolean,
    geom: Option[Array[Byte]],
    point: Option[(Double, Double)] = None
  ): RevGeoIndex = {
    val thriftModel = ThriftRevGeoIndex.newBuilder
      .id(new ObjectId)
      .cellId(cellid)
      .polyId(polyId)
      .applyIf(full, _.full(true))
      .geom(geom.map(ByteBuffer.wrap))
      .point(point.map({ case (lat, long) => Vector(lat, long) }))
      .result()
    new RevGeoIndex(thriftModel)
  }

  def makeIndexes(executor: IndexerQueryExecutor.ExecutorT): Unit = {
    val builder = MongoIndex.builder[ThriftRevGeoIndexMeta](ThriftRevGeoIndex)
    executor.createIndexes(ThriftRevGeoIndex)(
      builder.index(_.cellId, Desc)
    )
  }
}
