package io.fsq.twofishes.indexer.output

import io.fsq.common.scala.Identity._
import io.fsq.rogue.{InitialState, Iter, Query}
import io.fsq.twofishes.core.Indexes
import io.fsq.twofishes.gen.{CellGeometries, CellGeometry, YahooWoeType}
import io.fsq.twofishes.indexer.mongo.RevGeoIndex
import io.fsq.twofishes.indexer.mongo.RogueImplicits._
import io.fsq.twofishes.model.gen.{ThriftRevGeoIndex, ThriftRevGeoIndexMeta}
import io.fsq.twofishes.util.RevGeoConstants
import org.bson.types.ObjectId
import scala.collection.mutable.ListBuffer

class RevGeoIndexer(
  override val basepath: String,
  override val fidMap: FidMap,
  polygonMap: Map[ObjectId, List[(Long, YahooWoeType)]]
) extends Indexer
  with RevGeoConstants {
  val index = Indexes.S2Index
  override val outputs = Seq(index)

  lazy val writer = buildMapFileWriter(
    index,
    Map(
      "minS2Level" -> minS2LevelForRevGeo.toString,
      "maxS2Level" -> maxS2LevelForRevGeo.toString,
      "levelMod" -> defaultLevelModForRevGeo.toString
    )
  )

  type QueryType = Query[ThriftRevGeoIndexMeta, ThriftRevGeoIndex, InitialState]

  def writeRevGeoIndex(
    restrict: QueryType => QueryType
  ) = {
    val baseQuery: QueryType = Q(ThriftRevGeoIndex)
    val restrictedQuery: QueryType = restrict(baseQuery)
    val total: Long = executor.count(restrictedQuery)

    var currentKey = 0L
    var currentCells = new ListBuffer[CellGeometry]

    executor.iterate(
      restrictedQuery.orderAsc(_.cellId),
      0
    )((index: Int, event: Iter.Event[ThriftRevGeoIndex]) => {
      event match {
        case Iter.OnNext(unwrapped) => {
          val revgeoIndexRecord = new RevGeoIndex(unwrapped)
          for {
            (geoid, woeType) <- polygonMap.getOrElse(revgeoIndexRecord.polyIdOrThrow, Nil)
          } {
            if (index % 10000 =? 0) {
              log.info("processed %d of %d revgeo entries for %s".format(index, total, restrict))
            }
            if (currentKey !=? revgeoIndexRecord.cellIdOrThrow) {
              if (currentKey !=? 0L) {
                writer.append(currentKey, CellGeometries(currentCells))
              }
              currentKey = revgeoIndexRecord.cellIdOrThrow
              currentCells.clear
            }
            val builder = CellGeometry.newBuilder
              .woeType(woeType)
              .longId(geoid)

            if (revgeoIndexRecord.full) {
              builder.full(true)
            } else {
              builder.wkbGeometry(revgeoIndexRecord.geomOrThrow)
            }
            currentCells.append(builder.result)
          }
          Iter.Continue(index + 1)
        }
        case Iter.OnComplete => Iter.Return(index)
        case Iter.OnError(e) => throw e
      }
    })

    writer.append(currentKey, CellGeometries(currentCells))
  }

  def writeIndexImpl() {
    // in byte order, positives come before negative
    writeRevGeoIndex(_.scan(_.cellId gte 0))
    writeRevGeoIndex(_.scan(_.cellId lt 0))

    writer.close()
  }
}
