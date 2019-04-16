package io.fsq.twofishes.indexer.output

import com.twitter.util.{Await, Future, FuturePool}
import io.fsq.common.scala.Lists.Implicits._
import io.fsq.twofishes.indexer.mongo.IndexerQueryExecutor
import io.fsq.twofishes.indexer.mongo.RogueImplicits._
import io.fsq.twofishes.indexer.util.{GeocodeRecord, SlugEntryMap}
import io.fsq.twofishes.model.gen.ThriftGeocodeRecord
import io.fsq.twofishes.util.DurationUtils
import java.util.concurrent.{CountDownLatch, Executors}
import scala.collection.mutable.HashMap

class OutputIndexes(
  basepath: String,
  outputPrefixIndex: Boolean = true,
  slugEntryMap: SlugEntryMap.SlugEntryMap = HashMap.empty,
  outputRevgeo: Boolean = true,
  outputS2Covering: Boolean = true,
  outputS2Interior: Boolean = true
) extends DurationUtils {
  lazy val executor = IndexerQueryExecutor.instance

  def buildIndexes(s2CoveringLatch: Option[CountDownLatch]) {
    val fidMap = logPhase("preload fid map") { new FidMap(preload = true) }

    // This one wastes a lot of ram, so do it on it's own
    (new NameIndexer(basepath, fidMap, outputPrefixIndex)).writeIndex()

    // this should really really be done by now
    s2CoveringLatch.foreach(_.await())

    val hasPolyRecords = executor.fetch(Q(ThriftGeocodeRecord).scan(_.hasPoly eqs true)).map(new GeocodeRecord(_))
    val polygonMap = logPhase("preloading polygon map") {
      hasPolyRecords
        .map(r => (r.polyIdOrThrow, (r.id, r.woeTypeOrThrow)))
        .toList
        .groupBy(_._1)
        .mappedValues(v => v.map(_._2).toList)
        .toMap
    }

    val parallelizedIndexers = List(
      new IdIndexer(basepath, fidMap, slugEntryMap),
      new FeatureIndexer(basepath, fidMap, polygonMap),
      new PolygonIndexer(basepath, fidMap)
    ) ++ (if (outputRevgeo) {
            List(new RevGeoIndexer(basepath, fidMap, polygonMap))
          } else {
            Nil
          }) ++ (if (outputS2Covering) {
                   List(new S2CoveringIndexer(basepath, fidMap))
                 } else {
                   Nil
                 }) ++ (if (outputS2Interior) {
                          List(new S2InteriorIndexer(basepath, fidMap))
                        } else {
                          Nil
                        })

    val diskIoFuturePool = FuturePool(Executors.newFixedThreadPool(4))
    val indexFutures = parallelizedIndexers.map(indexer => diskIoFuturePool(indexer.writeIndex()))
    // wait forever to finish
    Await.result(Future.collect(indexFutures))

    log.info("all done with output")
  }
}
