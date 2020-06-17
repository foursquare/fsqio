package io.fsq.twofishes.indexer.output

import com.vividsolutions.jts.io.WKBReader
import io.fsq.common.scala.Identity._
import io.fsq.rogue.IterUtil
import io.fsq.twofishes.core.Indexes
import io.fsq.twofishes.indexer.mongo.PolygonIndex
import io.fsq.twofishes.indexer.mongo.RogueImplicits._
import io.fsq.twofishes.indexer.util.GeocodeRecord
import io.fsq.twofishes.model.gen.{ThriftGeocodeRecord, ThriftPolygonIndex}
import org.bson.types.ObjectId

class PolygonIndexer(override val basepath: String, override val fidMap: FidMap) extends Indexer {
  val index = Indexes.GeometryIndex
  override val outputs = Seq(index)

  def writeIndexImpl() {
    val polygonSize: Long = executor.count(Q(ThriftPolygonIndex))
    val usedPolygonSize: Long = executor.count(Q(ThriftGeocodeRecord).scan(_.hasPoly eqs true))

    val writer = buildMapFileWriter(index)

    val wkbReader = new WKBReader()

    var numUsedPolygon = 0
    val groupSize = 1000
    // would be great to unify this with featuresIndex
    executor.iterate(
      Q(ThriftGeocodeRecord).scan(_.hasPoly eqs true).orderAsc(_.id),
      (0, Vector[ThriftGeocodeRecord]()),
      batchSizeOpt = Some(groupSize)
    )(
      IterUtil.batch[Int, ThriftGeocodeRecord](
        groupSize,
        (groupIndex, unwrappedGroup) => {
          val group = unwrappedGroup.map(new GeocodeRecord(_))
          val toFindPolys: Map[Long, ObjectId] = group.filter(f => f.hasPoly).map(r => (r.id, r.polyIdOrThrow)).toMap
          val polyMap: Map[ObjectId, PolygonIndex] = executor
            .fetch(
              Q(ThriftPolygonIndex).where(_.id in toFindPolys.values)
            )
            .groupBy(_.id)
            .map({ case (k, v) => (k, new PolygonIndex(v(0))) })
          for {
            (f, polygonIndex) <- group.zipWithIndex
            poly <- polyMap.get(f.polyIdOrThrow)
          } {
            if (polygonIndex =? 0) {
              log.info(
                "PolygonIndexer: outputted %d of %d used polys, %d of %d total polys seen"
                  .format(numUsedPolygon, usedPolygonSize, polygonSize, groupIndex * groupSize)
              )
            }
            numUsedPolygon += 1
            writer.append(f.featureId, wkbReader.read(poly.polygonOrThrow.array()))
          }
          groupIndex + 1
        }
      )
    )

    writer.close()
    log.info("done")
  }
}
