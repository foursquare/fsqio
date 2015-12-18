package io.fsq.twofishes.server

import io.fsq.twofishes.gen.{GeocodeFeature, GeocodeInterpretation}
import io.fsq.twofishes.util.Identity._
import io.fsq.twofishes.util.Lists.Implicits._
import io.fsq.twofishes.util.StoredFeatureId
import scala.collection.JavaConverters._

trait BulkImplHelpers {
  def makeBulkReply[T](
      inputs: Seq[T],
      inputFids: Map[T, Seq[StoredFeatureId]],
      interps: Seq[GeocodeInterpretation]):
      (Seq[Seq[Int]], Seq[GeocodeInterpretation], Seq[GeocodeFeature]) = {
    val inputToInputIdxes: Map[T, Seq[Int]] = inputs.zipWithIndex.groupBy(_._1).mappedValues(_.map(_._2))

    val inputIdxToLongFids: Map[Int, Seq[Long]] = inputFids.flatMap({
      case (input, fids) => inputToInputIdxes.getOrElse(input, Nil).map(inputIdx => (inputIdx -> fids.map(_.longId)))
    })

    val featureIdToInterpIdxes: Map[Long, Seq[Int]] = interps.zipWithIndex
      .groupBy(_._1.feature.longId)
      .mappedValues(_.map(_._2))

    val inputIdxToInterpIdxs: Seq[Seq[Int]] = (for {
      inputIdx <- (0 to inputs.size - 1)
      longFids = inputIdxToLongFids.getOrElse(inputIdx, Nil)
    } yield {
      longFids.flatMap(longFid => featureIdToInterpIdxes(longFid))
    })

    (inputIdxToInterpIdxs, interps, Nil)
  }
}
