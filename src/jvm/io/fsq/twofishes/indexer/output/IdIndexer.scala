package io.fsq.twofishes.indexer.output

import com.mongodb.Bytes
import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.annotations._
import com.novus.salat.dao._
import com.novus.salat.global._
import io.fsq.twofishes.core.Indexes
import io.fsq.twofishes.indexer.mongo.MongoGeocodeDAO
import io.fsq.twofishes.indexer.util.SlugEntryMap
import io.fsq.twofishes.util.Identity._
import io.fsq.twofishes.util.StoredFeatureId
import java.io._
import org.apache.hadoop.hbase.util.Bytes._
import scala.collection.JavaConverters._

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

    val featureCursor = MongoGeocodeDAO.find(MongoDBObject())
    featureCursor.option = Bytes.QUERYOPTION_NOTIMEOUT
    val extraIds: List[(String, StoredFeatureId)]  = featureCursor.flatMap(f => {
      (for {
        id <- f.ids.filterNot(_ =? f._id)
        extraId <- StoredFeatureId.fromLong(id)
      } yield { List((id.toString -> f.featureId), (extraId.humanReadableString -> f.featureId)) }).flatten
    }).toList

    val writer = buildMapFileWriter(index)

    val sortedEntries = (slugEntries ++ extraIds).distinct.sortWith((a, b) => lexicalSort(a._1, b._1)).foreach({case (k, v) => {
      writer.append(k, v)
    }})

    writer.close()
  }
}
