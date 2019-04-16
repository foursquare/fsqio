package io.fsq.twofishes.indexer.output

import io.fsq.common.scala.Lists.Implicits._
import io.fsq.twofishes.core.Indexes
import io.fsq.twofishes.gen.{FeatureNameFlags, YahooWoeType}
import io.fsq.twofishes.indexer.mongo.NameIndex
import io.fsq.twofishes.indexer.mongo.RogueImplicits._
import io.fsq.twofishes.model.gen.ThriftNameIndex
import io.fsq.twofishes.util.StoredFeatureId
import java.util.regex.Pattern
import scala.collection.mutable.HashSet

object PrefixIndexer {
  val MaxPrefixLength = 5
  val MaxNamesToConsider = 1000
  val MaxFidsToStorePerPrefix = 50
  val MaxFidsWithPreferredNamesBeforeConsideringNonPreferred = 3
  val index = Indexes.PrefixIndex
}

class PrefixIndexer(
  override val basepath: String,
  override val fidMap: FidMap,
  prefixSet: HashSet[String]
) extends Indexer {
  val index = PrefixIndexer.index
  override val outputs = Seq(index)

  def hasFlag(record: NameIndex, flag: FeatureNameFlags) =
    (record.flags & flag.getValue) > 0

  def joinLists(lists: List[NameIndex]*): List[NameIndex] = {
    lists.toList.flatMap(l => {
      l.sortBy(_.pop * -1)
    })
  }

  private def roundRobinByCountryCode(records: List[NameIndex]): List[NameIndex] = {
    // to ensure global distribution of features from all countries, group by cc
    // and then pick the top from each group by turn and cycle through
    // input: a (US), b (US), c (CN), d (US), e (AU), f (AU), g (CN)
    // desired output: a (US), c (CN), e (AU), b (US), g (CN), f (AU), d (US)
    records
      .flatGroupBy(_.ccOption) // (US -> a, b, d), (CN -> c, g), (AU -> e, f)
      .values
      .toList // (a, b, d), (c, g), (e, f)
      .flatMap(_.zipWithIndex) // (a, 0), (b, 1), (d, 2), (c, 0), (g, 1), (e, 0), (f, 1)
      .groupBy(_._2)
      .toList // (0 -> a, c, e), (1 -> b, g, f), (2 -> d)
      .sortBy(_._1)
      .flatMap(_._2.map(_._1)) // a, c, e, b, g, f, d
  }

  def sortRecordsByNames(records: List[NameIndex]) = {
    val (prefPureNames, nonPrefPureNames) =
      records.partition(
        r =>
          (hasFlag(r, FeatureNameFlags.PREFERRED) || hasFlag(r, FeatureNameFlags.ALT_NAME)) &&
            (r.langOption.has("en") || hasFlag(r, FeatureNameFlags.LOCAL_LANG))
      )

    val (secondBestNames, worstNames) =
      nonPrefPureNames.partition(
        r =>
          r.langOption.has("en")
            || hasFlag(r, FeatureNameFlags.LOCAL_LANG)
      )

    (joinLists(prefPureNames), joinLists(secondBestNames, worstNames))
  }

  def getRecordsByPrefix(prefix: String, limit: Int) = {
    val names = executor.fetch(
      Q(ThriftNameIndex)
        .scan(_.name eqs prefix)
        .scan(_.excludeFromPrefixIndex neqs true)
        .orderDesc(_.pop)
        .limit(limit)
    )
    val prefixes = executor.fetch(
      Q(ThriftNameIndex)
        .where(_.name matches Pattern.compile("^%s".format(prefix)))
        .scan(_.excludeFromPrefixIndex neqs true)
        .orderDesc(_.pop)
        .limit(limit)
    )
    (names ++ prefixes).distinct.take(limit).map(new NameIndex(_))
  }

  def writeIndexImpl() {
    log.info("sorting prefix set")
    val sortedPrefixes = prefixSet.toList.sortWith(lexicalSort)
    log.info("done sorting")

    val bestWoeTypes = List(
      YahooWoeType.POSTAL_CODE,
      YahooWoeType.TOWN,
      YahooWoeType.SUBURB,
      YahooWoeType.ADMIN3,
      YahooWoeType.AIRPORT,
      YahooWoeType.ADMIN1,
      YahooWoeType.COUNTRY
    ).map(_.getValue)

    val prefixWriter = buildMapFileWriter(
      index,
      Map(
        "MAX_PREFIX_LENGTH" -> PrefixIndexer.MaxPrefixLength.toString,
        "MAX_FIDS_PER_PREFIX" -> PrefixIndexer.MaxFidsToStorePerPrefix.toString
      )
    )

    val numPrefixes = sortedPrefixes.size
    for {
      (prefix, index) <- sortedPrefixes.zipWithIndex
    } {
      if (index % 1000 == 0) {
        log.info("done with %d of %d prefixes".format(index, numPrefixes))
      }
      val records = getRecordsByPrefix(prefix, PrefixIndexer.MaxNamesToConsider)

      val (woeMatches, woeMismatches) = records.partition(r => bestWoeTypes.contains(r.woeTypeOrThrow))

      val (prefSortedRecords, unprefSortedRecords) =
        sortRecordsByNames(woeMatches.toList)

      val fids = new HashSet[StoredFeatureId]
      //roundRobinByCountryCode(prefSortedRecords).foreach(f => {
      prefSortedRecords.foreach(f => {
        if (fids.size < PrefixIndexer.MaxFidsToStorePerPrefix) {
          fids.add(f.fidAsFeatureId)
        }
      })

      if (fids.size < PrefixIndexer.MaxFidsWithPreferredNamesBeforeConsideringNonPreferred) {
        //roundRobinByCountryCode(unprefSortedRecords).foreach(f => {
        unprefSortedRecords.foreach(f => {
          if (fids.size < PrefixIndexer.MaxFidsToStorePerPrefix) {
            fids.add(f.fidAsFeatureId)
          }
        })
      }

      prefixWriter.append(prefix, fidsToCanonicalFids(fids.toList))
    }

    prefixWriter.close()
    log.info("done")
  }
}
