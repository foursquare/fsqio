package io.fsq.twofishes.indexer.output

import com.vividsolutions.jts.io.WKBReader
import io.fsq.common.scala.Identity._
import io.fsq.rogue.IterUtil
import io.fsq.twofishes.core.Indexes
import io.fsq.twofishes.gen.{GeocodeServingFeature, YahooWoeType}
import io.fsq.twofishes.indexer.mongo.{IndexerQueryExecutor, PolygonIndex, RevGeoIndex}
import io.fsq.twofishes.indexer.mongo.RogueImplicits._
import io.fsq.twofishes.indexer.util.GeocodeRecord
import io.fsq.twofishes.model.gen.{ThriftGeocodeRecord, ThriftPolygonIndex, ThriftRevGeoIndex}
import io.fsq.twofishes.util.{GeoTools, GeometryUtils, StoredFeatureId}
import org.bson.types.ObjectId
import scala.collection.JavaConverters._

class FeatureIndexer(
  override val basepath: String,
  override val fidMap: FidMap,
  polygonMap: Map[ObjectId, List[(Long, YahooWoeType)]]
) extends Indexer {
  def canonicalizeParentId(fid: StoredFeatureId) = fidMap.get(fid)

  val index = Indexes.FeatureIndex
  override val outputs = Seq(index)

  def makeGeocodeRecordWithoutGeometry(g: GeocodeRecord, poly: Option[PolygonIndex]): GeocodeServingFeature = {
    val fullFeature = (for {
      p <- poly
      polygon <- p.polygonOption
      source <- p.sourceOption
    } yield {
      new GeocodeRecord(
        g.toBuilder
          .polygon(polygon)
          .polygonSource(source)
          .result()
      )
    }).getOrElse(g).toGeocodeServingFeature()

    val partialFeature = fullFeature.copy(
      feature = fullFeature.feature.copy(
        geometry = fullFeature.feature.geometryOrThrow.copy(wkbGeometry = null)
      )
    )

    makeGeocodeServingFeature(partialFeature)
  }

  def makeGeocodeRecord(g: GeocodeRecord) = {
    makeGeocodeServingFeature(g.toGeocodeServingFeature())
  }

  val wkbReader = new WKBReader()
  def makeGeocodeServingFeature(f: GeocodeServingFeature) = {
    var parents = (for {
      parentLongId <- f.scoringFeatures.parentIds
      parentFid <- StoredFeatureId.fromLong(parentLongId)
      parentId <- canonicalizeParentId(parentFid)
    } yield {
      parentFid
    }).map(_.longId)

    if (f.scoringFeatures.parentIds.isEmpty &&
        f.feature.woeType !=? YahooWoeType.COUNTRY) {
      // take the center and reverse geocode it against the revgeo index!
      val geom = GeoTools.pointToGeometry(f.feature.geometryOrNull.center)
      val cells: Seq[Long] = GeometryUtils.s2PolygonCovering(geom).map(_.id)

      // now for each cell, find the matches in our index
      val candidates = IndexerQueryExecutor.instance
        .fetch(
          Q(ThriftRevGeoIndex)
            .where(_.cellId in cells)
        )
        .map(new RevGeoIndex(_))

      // for each candidate, check if it's full or we're in it
      val matches = (for {
        revGeoCell <- candidates
        fidLong <- polygonMap.getOrElse(revGeoCell.polyIdOrThrow, Nil)
        if (revGeoCell.full || revGeoCell.geomOption.exists(
          geomBuffer => wkbReader.read(geomBuffer.array()).contains(geom)
        ))
      } yield { fidLong }).toList

      parents = matches.map(_._1)
    }

    f.copy(
      scoringFeatures = f.scoringFeatures.copy(parentIds = parents)
    )
  }

  def writeIndexImpl() {
    val writer = buildMapFileWriter(index, indexInterval = Some(2))
    var fidCount = 0

    val fidSize: Long = executor.count(Q(ThriftGeocodeRecord))
    executor.iterate(
      Q(ThriftGeocodeRecord).orderAsc(_.id),
      ((), Vector[ThriftGeocodeRecord]()),
      batchSizeOpt = Some(1000)
    )(
      IterUtil.batch(
        1000,
        (_: Unit, group: Vector[ThriftGeocodeRecord]) => {
          val toFindPolys: Map[Long, ObjectId] = group.filter(f => f.hasPoly).map(r => (r.id, r.polyIdOrThrow)).toMap
          val polyMap: Map[ObjectId, PolygonIndex] = executor
            .fetch(
              Q(ThriftPolygonIndex)
                .where(_.id in toFindPolys.values)
            )
            .groupBy(_.id)
            .map({ case (k, v) => (k, new PolygonIndex(v(0))) })
          group.foreach(unwrapped => {
            val f = new GeocodeRecord(unwrapped)
            val polyOpt = polyMap.get(f.polyIdOrThrow)
            writer.append(f.featureId, makeGeocodeRecordWithoutGeometry(f, polyOpt))
            fidCount += 1
            if (fidCount % 100000 == 0) {
              log.info("processed %d of %d features".format(fidCount, fidSize))
            }
          })
        }
      )
    )

    writer.close()
  }
}
