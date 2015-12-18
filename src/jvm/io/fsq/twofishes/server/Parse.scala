//  Copyright 2012 Foursquare Labs Inc. All Rights Reserved
package io.fsq.twofishes.server

import io.fsq.twofishes.gen.{DebugScoreComponent, FeatureName, GeocodeFeature, GeocodeServingFeature,
    InterpretationScoringFeatures}
import io.fsq.twofishes.util.{GeoTools, StoredFeatureId}
import io.fsq.twofishes.util.Identity._
import io.fsq.twofishes.util.Lists.Implicits._
import io.fsq.twofishes.util.NameUtils
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer

// Represents a match from a run of tokens to one particular feature
case class FeatureMatch(
  tokenStart: Int,
  tokenEnd: Int,
  phrase: String,
  fmatch: GeocodeServingFeature,
  possibleNameHits: Seq[FeatureName] = Nil
)

// Sort a list of feature matches, smallest to biggest
object FeatureMatchOrdering extends Ordering[FeatureMatch] {
  def compare(a: FeatureMatch, b: FeatureMatch) = {
    GeocodeServingFeatureOrdering.compare(a.fmatch, b.fmatch)
  }
}

trait MaybeSorted
trait Sorted extends MaybeSorted
trait Unsorted extends MaybeSorted

// Parse = one particular interpretation of the query
case class Parse[T <: MaybeSorted](
  fmatches: Seq[FeatureMatch]
) extends Seq[FeatureMatch] {
  def apply(i: Int) = fmatches(i)
  def iterator = fmatches.iterator
  def length = fmatches.length

  lazy val scoreKey = fmatches.map(_.fmatch.longId).mkString(":")

  def primaryFeature = fmatches(0)

  val debugLines = new ListBuffer[DebugScoreComponent]
  var finalScore = 0
  var scoringFeaturesOption: Option[InterpretationScoringFeatures] = None
  var allLongIds: Seq[Long] = Nil
  lazy val extraLongIds: Seq[Long] = allLongIds.filterNot(_ =? featureId.longId)

  def addDebugLine(component: DebugScoreComponent) {
    debugLines.append(component)
  }

  def setFinalScore(score: Int) { finalScore = score }

  def setScoringFeatures(scoringFeaturesIn: Option[InterpretationScoringFeatures]) {
    scoringFeaturesOption = scoringFeaturesIn
  }

  override def toString: String = {
    val namesandids = this.map(f => {
      val name = NameUtils.bestName(f.fmatch.feature, None, false).map(_.name).getOrElse("UNKNOWN")
      val cc = f.fmatch.feature.ccOrThrow
      val id = f.fmatch.feature.ids.headOption.map(
        fid => "%s:%s".format(fid.source, fid.id)).getOrElse("no:id")
      "%s %s %s".format(id, name, cc)
    })
    "%d features: %s".format(this.size, namesandids.mkString(","))
  }

  def tokenLength = fmatches.map(pp => pp.tokenEnd - pp.tokenStart).sum

  def getSorted: Parse[Sorted] = Parse.makeSortedParse(fmatches, scoringFeaturesOption)

  def addFeature(f: FeatureMatch) = Parse[Unsorted](fmatches ++ List(f))

  def addSortedFeature(f: FeatureMatch) =
    Parse.makeSortedParse(fmatches ++ List(f), scoringFeaturesOption)

  def countryCode = fmatches.headOption.map(_.fmatch.feature.ccOrThrow).getOrElse("XX")

  lazy val featureId = StoredFeatureId.fromLong(fmatches(0).fmatch.longId).get

  def mostSpecificFeature[Sorted] = fmatches(0)

  def hasDupeFeature: Boolean = {
    this.headOption.exists(primaryFeature => {
      val rest = this.drop(1)
      rest.exists(_.fmatch.feature.ids == primaryFeature.fmatch.feature.ids)
    })
  }
}

object Parse {
  def makeSortedParse(
    fmatches: Seq[FeatureMatch],
    scoringFeaturesOption: Option[InterpretationScoringFeatures]
  ) = {
    val sortedParse = Parse[Sorted](fmatches.sorted(FeatureMatchOrdering))
    sortedParse.setScoringFeatures(scoringFeaturesOption)
    sortedParse
  }
}

object ParseUtils {
  def featureDistance(f1: GeocodeFeature, f2: GeocodeFeature) = {
    GeoTools.getDistance(
      f1.geometryOrThrow.center.lat,
      f1.geometryOrThrow.center.lng,
      f2.geometryOrThrow.center.lat,
      f2.geometryOrThrow.center.lng)
  }

  def boundsContains(f1: GeocodeFeature, f2: GeocodeFeature) = {
    f1.geometryOrThrow.boundsOption.exists(bb =>
      GeoTools.boundsContains(bb, f2.geometryOrThrow.center)) ||
    f2.geometryOrThrow.boundsOption.exists(bb =>
      GeoTools.boundsContains(bb, f1.geometryOrThrow.center))
  }

  def parsesNear(p1: Parse[Sorted], p2: Parse[Sorted]): Boolean = {
    (p1.headOption, p2.headOption) match {
      case (Some(sf1), Some(sf2)) => {
        (featureDistance(sf1.fmatch.feature, sf2.fmatch.feature) < 15000) ||
        boundsContains(sf1.fmatch.feature, sf2.fmatch.feature)
      }
      case _ => false
    }
  }
}
