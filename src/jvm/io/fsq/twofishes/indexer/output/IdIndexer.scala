package io.fsq.twofishes.indexer.output

import io.fsq.common.scala.Identity._
import io.fsq.rogue.Iter
import io.fsq.twofishes.core.Indexes
import io.fsq.twofishes.indexer.mongo.IndexerQueryExecutor
import io.fsq.twofishes.indexer.mongo.RogueImplicits._
import io.fsq.twofishes.indexer.util.{GeocodeRecord, SlugEntryMap}
import io.fsq.twofishes.model.gen.ThriftGeocodeRecord
import io.fsq.twofishes.util.StoredFeatureId

class IdIndexer(
  override val basepath: String,
  override val fidMap: FidMap,
  slugEntryMap: SlugEntryMap.SlugEntryMap
) extends Indexer {
  val index = Indexes.IdMappingIndex
  override val outputs = Seq(index)

  def writeIndexImpl() {
    val slugEntries: List[(String, StoredFeatureId)] = for {
      (slug, entry) <- slugEntryMap.toList
      fid <- StoredFeatureId.fromHumanReadableString(entry.id)
      canonicalFid <- fidMap.get(fid)
    } yield {
      slug -> canonicalFid
    }

    val extraIds: List[(String, StoredFeatureId)] = IndexerQueryExecutor.instance.iterate(
      Q(ThriftGeocodeRecord),
      List.empty[(String, StoredFeatureId)]
    )((acc: List[(String, StoredFeatureId)], event: Iter.Event[ThriftGeocodeRecord]) => {
      event match {
        case Iter.OnNext(unwrapped) => {
          val f = new GeocodeRecord(unwrapped)
          val items = (for {
            id <- f.ids.filterNot(_ =? f.id)
            extraId <- StoredFeatureId.fromLong(id)
          } yield { List((id.toString -> f.featureId), (extraId.humanReadableString -> f.featureId)) }).flatten
          // Even though we build this list in reverse, it gets sorted anyway below. Yay O(1) prepend.
          Iter.Continue(items.toList ::: acc)
        }
        case Iter.OnComplete => Iter.Return(acc)
        case Iter.OnError(e) => throw e
      }
    })

    val writer = buildMapFileWriter(index)

    val sortedEntries = (slugEntries ++ extraIds).distinct
      .sortWith((a, b) => lexicalSort(a._1, b._1))
      .foreach({
        case (k, v) => {
          writer.append(k, v)
        }
      })

    writer.close()
  }
}
