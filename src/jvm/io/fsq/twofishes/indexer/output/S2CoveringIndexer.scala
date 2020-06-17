package io.fsq.twofishes.indexer.output

import io.fsq.rogue.IterUtil
import io.fsq.twofishes.core.Indexes
import io.fsq.twofishes.indexer.mongo.RogueImplicits._
import io.fsq.twofishes.indexer.util.GeocodeRecord
import io.fsq.twofishes.model.gen.{ThriftGeocodeRecord, ThriftS2CoveringIndex}
import io.fsq.twofishes.util.S2CoveringConstants
import org.bson.types.ObjectId

class S2CoveringIndexer(
  override val basepath: String,
  override val fidMap: FidMap
) extends Indexer
  with S2CoveringConstants {
  val index = Indexes.S2CoveringIndex
  override val outputs = Seq(index)

  def writeIndexImpl() {
    val polygonSize: Long = executor.count(Q(ThriftS2CoveringIndex))
    val usedPolygonSize: Long = executor.count(Q(ThriftGeocodeRecord).where(_.hasPoly eqs true))

    val writer = buildMapFileWriter(
      index,
      Map(
        "minS2Level" -> minS2LevelForS2Covering.toString,
        "maxS2Level" -> maxS2LevelForS2Covering.toString,
        "levelMod" -> defaultLevelModForS2Covering.toString
      )
    )

    var numUsedPolygon = 0
    val groupSize = 1000
    // would be great to unify this with featuresIndex
    executor.iterate(
      Q(ThriftGeocodeRecord)
        .where(_.hasPoly eqs true)
        .orderAsc(_.id),
      (0, Vector[ThriftGeocodeRecord]()),
      batchSizeOpt = Some(groupSize)
    )(
      IterUtil.batch(
        groupSize,
        (groupIndex: Int, unwrappedGroup: Vector[ThriftGeocodeRecord]) => {
          val group = unwrappedGroup.map(new GeocodeRecord(_))
          val toFindCovers: Map[Long, ObjectId] = group.filter(f => f.hasPoly).map(r => (r.id, r.polyIdOrThrow)).toMap
          val coverMap: Map[ObjectId, ThriftS2CoveringIndex] = executor
            .fetch(
              Q(ThriftS2CoveringIndex).where(_.id in toFindCovers.values)
            )
            .groupBy(_.id)
            .map({ case (k, v) => (k, v(0)) })
          for {
            (f, coverIndex) <- group.zipWithIndex
            covering <- coverMap.get(f.polyIdOrThrow)
          } {
            if (coverIndex == 0) {
              log.info(
                "S2CoveringIndexer: outputted %d of %d used polys, %d of %d total polys seen"
                  .format(numUsedPolygon, usedPolygonSize, polygonSize, groupIndex * groupSize)
              )
            }
            numUsedPolygon += 1
            writer.append(f.featureId, covering.cellIds)
          }
          groupIndex + 1
        }
      )
    )

    writer.close()

    log.info("done")
  }
}
