package io.fsq.twofishes.indexer.output

import io.fsq.rogue.Iter
import io.fsq.twofishes.indexer.mongo.IndexerQueryExecutor
import io.fsq.twofishes.indexer.mongo.RogueImplicits._
import io.fsq.twofishes.indexer.util.GeocodeRecord
import io.fsq.twofishes.model.gen.ThriftGeocodeRecord
import io.fsq.twofishes.util.{DurationUtils, StoredFeatureId}
import scala.collection.mutable.HashMap

class FidMap(preload: Boolean) extends DurationUtils {
  val fidMap = new HashMap[StoredFeatureId, Option[StoredFeatureId]]

  lazy val executor = IndexerQueryExecutor.instance

  if (preload) {
    logPhase("preloading fids") {
      var i = 0
      val total: Long = executor.count(Q(ThriftGeocodeRecord))
      executor.iterate(
        Q(ThriftGeocodeRecord),
        ()
      )((_: Unit, event: Iter.Event[ThriftGeocodeRecord]) => {
        event match {
          case Iter.OnNext(unwrapped) => {
            val geocodeRecord = new GeocodeRecord(unwrapped)
            geocodeRecord.featureIds.foreach(id => {
              fidMap(id) = Some(geocodeRecord.featureId)
            })
            i += 1
            if (i % (100 * 1000) == 0) {
              log.info("preloaded %d/%d fids".format(i, total))
            }
            Iter.Continue(())
          }
          case Iter.OnComplete => Iter.Return(())
          case Iter.OnError(e) => throw e
        }
      })
    }
  }

  def get(fid: StoredFeatureId): Option[StoredFeatureId] = {
    if (preload) {
      fidMap.getOrElse(fid, None)
    } else {
      if (!fidMap.contains(fid)) {
        val longidOpt = executor
          .fetchOne(
            Q(ThriftGeocodeRecord).where(_.id eqs fid.longId).select(_.id)
          )
          .flatten
        fidMap(fid) = longidOpt.flatMap(StoredFeatureId.fromLong _)
        if (longidOpt.isEmpty) {
          //println("missing fid: %s".format(fid))
        }
      }

      fidMap.getOrElseUpdate(fid, None)
    }
  }
}
