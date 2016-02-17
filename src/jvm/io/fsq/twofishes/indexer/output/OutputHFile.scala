package io.fsq.twofishes.indexer.output

import com.mongodb.Bytes
import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.annotations._
import com.novus.salat.dao._
import com.novus.salat.global._
import com.twitter.util.{Await, Future, FuturePool}
import io.fsq.common.scala.Lists.Implicits._
import io.fsq.twofishes.indexer.mongo.MongoGeocodeDAO
import io.fsq.twofishes.indexer.util.SlugEntryMap
import io.fsq.twofishes.util.DurationUtils
import java.io._
import java.util.concurrent.{CountDownLatch, Executors}
import org.apache.hadoop.hbase.util.Bytes._
import scala.collection.JavaConverters._
import scala.collection.mutable.HashMap

class OutputIndexes(
  basepath: String,
  outputPrefixIndex: Boolean = true,
  slugEntryMap: SlugEntryMap.SlugEntryMap = HashMap.empty,
  outputRevgeo: Boolean = true,
  outputS2Covering: Boolean = true,
  outputS2Interior: Boolean = true
) extends DurationUtils {
  def buildIndexes(s2CoveringLatch: Option[CountDownLatch]) {
    val fidMap = logPhase("preload fid map") { new FidMap(preload = true) }

    // This one wastes a lot of ram, so do it on it's own
    (new NameIndexer(basepath, fidMap, outputPrefixIndex)).writeIndex()

    // this should really really be done by now
    s2CoveringLatch.foreach(_.await())

    val hasPolyCursor =
      MongoGeocodeDAO.find(MongoDBObject("hasPoly" -> true))
    hasPolyCursor.option = Bytes.QUERYOPTION_NOTIMEOUT
    val polygonMap = logPhase("preloading polygon map") {
      hasPolyCursor.map(r => (r.polyId, (r._id, r.woeType))).toList
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
    } else { Nil }) ++ (if (outputS2Covering) {
      List(new S2CoveringIndexer(basepath, fidMap))
    } else { Nil }) ++ (if (outputS2Interior) {
      List(new S2InteriorIndexer(basepath, fidMap))
    } else { Nil })


    val diskIoFuturePool = FuturePool(Executors.newFixedThreadPool(4))
    val indexFutures = parallelizedIndexers.map(indexer =>
      diskIoFuturePool(indexer.writeIndex())
    )
    // wait forever to finish
    Await.result(Future.collect(indexFutures))

    log.info("all done with output")
  }
}
